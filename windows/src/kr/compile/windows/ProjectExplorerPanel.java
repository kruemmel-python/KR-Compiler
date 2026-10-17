package kr.compile.windows;

import kr.compile.ArtifactsManager;
import kr.compile.DocumentManager;
import kr.compile.ProjectManager;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

public final class ProjectExplorerPanel extends JPanel implements ProjectManager.Listener {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JLabel headerLabel;

    public ProjectExplorerPanel() {
        super(new BorderLayout());
        setBackground(new Color(37, 37, 38));

        // Toolbar oben
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        toolbar.setBackground(new Color(45, 45, 48));

        headerLabel = new JLabel("DATEIEN");
        headerLabel.setForeground(Color.LIGHT_GRAY);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        toolbar.add(headerLabel);

        JButton btnRefresh = new JButton("↻");
        btnRefresh.setToolTipText("Projektstruktur aktualisieren");
        btnRefresh.addActionListener(e -> ProjectManager.getInstance().refreshTree());
        toolbar.add(btnRefresh);

        JButton btnOpenExp = new JButton("📂");
        btnOpenExp.setToolTipText("Im Windows Explorer öffnen");
        btnOpenExp.addActionListener(e -> {
            File p = ProjectManager.getInstance().getActiveProjectDir();
            if (p != null) {
                try {
                    ArtifactsManager.getInstance().openInExplorer(p);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Fehler beim Öffnen: " + ex.getMessage());
                }
            }
        });
        toolbar.add(btnOpenExp);

        add(toolbar, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode("Kein Projekt");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setBackground(new Color(30, 30, 30));
        tree.setForeground(new Color(220, 220, 220));

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackgroundNonSelectionColor(new Color(30, 30, 30));
        renderer.setTextNonSelectionColor(new Color(220, 220, 220));
        renderer.setTextSelectionColor(Color.WHITE);
        renderer.setBackgroundSelectionColor(new Color(9, 71, 113));
        tree.setCellRenderer(renderer);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!(node.getUserObject() instanceof FileItem item)) return;

                if (e.getClickCount() == 2 && item.file.isFile()) {
                    File root = ProjectManager.getInstance().getActiveProjectDir();
                    try {
                        DocumentManager.getInstance().openDocument(root, item.file);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(ProjectExplorerPanel.this, "Konnte Datei nicht öffnen: " + ex.getMessage());
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e.getX(), e.getY(), item);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        ProjectManager.getInstance().addListener(this);
    }

    private static final class FileItem {
        final File file;
        FileItem(File file) { this.file = file; }
        @Override public String toString() { return file.getName(); }
    }

    private void showContextMenu(int x, int y, FileItem item) {
        JPopupMenu menu = new JPopupMenu();
        File targetDir = item.file.isDirectory() ? item.file : item.file.getParentFile();

        JMenuItem mNewFile = new JMenuItem("Neue Datei …");
        mNewFile.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Dateiname:", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                File nf = new File(targetDir, name.trim());
                try {
                    if (nf.createNewFile()) {
                        ProjectManager.getInstance().refreshTree();
                        DocumentManager.getInstance().openDocument(ProjectManager.getInstance().getActiveProjectDir(), nf);
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage());
                }
            }
        });
        menu.add(mNewFile);

        JMenuItem mNewDir = new JMenuItem("Neuer Ordner …");
        mNewDir.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Ordnername:", "Neuer Ordner", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                File nd = new File(targetDir, name.trim());
                if (nd.mkdirs()) ProjectManager.getInstance().refreshTree();
            }
        });
        menu.add(mNewDir);

        menu.addSeparator();

        JMenuItem mRename = new JMenuItem("Umbenennen …");
        mRename.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Neuer Name:", item.file.getName());
            if (name != null && !name.trim().isEmpty()) {
                File dest = new File(item.file.getParentFile(), name.trim());
                if (item.file.renameTo(dest)) ProjectManager.getInstance().refreshTree();
            }
        });
        menu.add(mRename);

        JMenuItem mDelete = new JMenuItem("Löschen");
        mDelete.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Möchten Sie '" + item.file.getName() + "' wirklich löschen?", "Löschen", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                deleteRecursive(item.file);
                ProjectManager.getInstance().refreshTree();
            }
        });
        menu.add(mDelete);

        menu.addSeparator();

        JMenuItem mExp = new JMenuItem("Im Windows Explorer zeigen");
        mExp.addActionListener(e -> {
            try {
                ArtifactsManager.getInstance().openInExplorer(item.file);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage());
            }
        });
        menu.add(mExp);

        menu.show(tree, x, y);
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File sub : c) deleteRecursive(sub);
        }
        f.delete();
    }

    @Override
    public void onProjectOpened(File projectDir) {
        headerLabel.setText(projectDir.getName().toUpperCase());
        reloadTree(projectDir);
    }

    @Override
    public void onProjectClosed() {
        headerLabel.setText("DATEIEN");
        rootNode.setUserObject("Kein Projekt");
        rootNode.removeAllChildren();
        treeModel.reload();
    }

    @Override
    public void onTreeRefreshed(File projectDir) {
        reloadTree(projectDir);
    }

    private void reloadTree(File projectDir) {
        rootNode.setUserObject(new FileItem(projectDir));
        rootNode.removeAllChildren();
        populate(rootNode, projectDir);
        treeModel.reload();
        tree.expandRow(0);
    }

    private void populate(DefaultMutableTreeNode parentNode, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing((File f) -> !f.isDirectory()).thenComparing(File::getName));
        for (File f : files) {
            if (f.getName().startsWith(".") || f.getName().equals("KR_BUILD")) continue;
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FileItem(f));
            parentNode.add(child);
            if (f.isDirectory()) {
                populate(child, f);
            }
        }
    }
}
