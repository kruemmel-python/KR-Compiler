package kr.compile.linux;

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
import java.util.Arrays;
import java.util.Comparator;

public final class ProjectExplorerPanel extends JPanel implements ProjectManager.Listener {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JLabel headerLabel;

    public ProjectExplorerPanel() {
        super(new BorderLayout());
        setBackground(DarkTheme.BG_PANEL);

        // Toolbar oben
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        toolbar.setBackground(DarkTheme.BG_TOOLBAR);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_DARK));

        headerLabel = new JLabel("DATEIEN");
        headerLabel.setForeground(DarkTheme.FG_PRIMARY);
        headerLabel.setFont(DarkTheme.FONT_BOLD);
        toolbar.add(headerLabel);

        toolbar.add(Box.createHorizontalStrut(8));

        JButton btnRefresh = DarkTheme.createIconButton("↺", "Projektstruktur aktualisieren");
        btnRefresh.addActionListener(e -> ProjectManager.getInstance().refreshTree());
        toolbar.add(btnRefresh);

        JButton btnOpenExp = DarkTheme.createIconButton("↗", "Im Windows Explorer öffnen");
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
        tree.setBackground(DarkTheme.BG_PANEL);
        tree.setForeground(DarkTheme.FG_PRIMARY);
        tree.setFont(DarkTheme.FONT_REGULAR);
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackgroundNonSelectionColor(DarkTheme.BG_PANEL);
        renderer.setTextNonSelectionColor(DarkTheme.FG_PRIMARY);
        renderer.setTextSelectionColor(Color.WHITE);
        renderer.setBackgroundSelectionColor(DarkTheme.BG_SELECT);
        renderer.setBorderSelectionColor(DarkTheme.ACCENT_BLUE);
        renderer.setFont(DarkTheme.FONT_REGULAR);
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
        scroll.getViewport().setBackground(DarkTheme.BG_PANEL);
        scroll.setBackground(DarkTheme.BG_PANEL);
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
        menu.setBackground(DarkTheme.BG_TOOLBAR);
        menu.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_DARK));

        File targetDir = item.file.isDirectory() ? item.file : item.file.getParentFile();

        JMenuItem mNewFile = createMenuItem("Neue Datei …", () -> {
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

        JMenuItem mNewDir = createMenuItem("Neuer Ordner …", () -> {
            String name = JOptionPane.showInputDialog(this, "Ordnername:", "Neuer Ordner", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                File nd = new File(targetDir, name.trim());
                if (nd.mkdirs()) ProjectManager.getInstance().refreshTree();
            }
        });
        menu.add(mNewDir);

        menu.addSeparator();

        JMenuItem mRename = createMenuItem("Umbenennen …", () -> {
            String name = JOptionPane.showInputDialog(this, "Neuer Name:", item.file.getName());
            if (name != null && !name.trim().isEmpty()) {
                File dest = new File(item.file.getParentFile(), name.trim());
                if (item.file.renameTo(dest)) ProjectManager.getInstance().refreshTree();
            }
        });
        menu.add(mRename);

        JMenuItem mDelete = createMenuItem("Löschen", () -> {
            int c = JOptionPane.showConfirmDialog(this, "Möchten Sie '" + item.file.getName() + "' wirklich löschen?", "Löschen", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                deleteRecursive(item.file);
                ProjectManager.getInstance().refreshTree();
            }
        });
        menu.add(mDelete);

        menu.addSeparator();

        JMenuItem mExp = createMenuItem("Im Windows Explorer zeigen", () -> {
            try {
                ArtifactsManager.getInstance().openInExplorer(item.file);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage());
            }
        });
        menu.add(mExp);

        menu.show(tree, x, y);
    }

    private JMenuItem createMenuItem(String title, Runnable action) {
        JMenuItem item = new JMenuItem(title);
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI());
        item.setBackground(DarkTheme.BG_TOOLBAR);
        item.setForeground(DarkTheme.FG_PRIMARY);
        item.setFont(DarkTheme.FONT_REGULAR);
        item.addActionListener(e -> action.run());
        return item;
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
