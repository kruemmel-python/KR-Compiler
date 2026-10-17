package kr.compile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

/** Self-contained agent UI; the existing editor and build tabs stay untouched. */
public final class KrAgentDialog {
    private static KrAgentCore agent;
    private KrAgentDialog() {}
    private static EditText field(Activity a,String hint,String value,int lines) {
        EditText e=new EditText(a);e.setHint(hint);e.setText(value);e.setTextSize(14);
        e.setSingleLine(lines==1); if(lines>1)e.setMinLines(lines);
        return e;
    }
    public static void show(Activity a) {
        if(agent==null)agent=new KrAgentCore(a.getApplicationContext());
        SharedPreferences prefs=a.getSharedPreferences("kr_agent_settings",Activity.MODE_PRIVATE);
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,12,24,12);
        TextView disclaimer=new TextView(a);
        disclaimer.setText("KR Agent – lokale Projekte. Änderungen und Builds benötigen Freigabe. Keine Änderungen am Compilerkern.");
        body.addView(disclaimer);
        EditText endpoint=field(a,"OpenAI-kompatibler HTTPS-Endpunkt",prefs.getString("endpoint","https://api.openai.com/v1/chat/completions"),1);
        EditText model=field(a,"Modellname",prefs.getString("model",""),1);
        EditText token=field(a,"API-Key (nur in dieser Sitzung)","",1);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText task=field(a,"Auftrag: z.B. Lies mein Projekt, schreibe einen Test und kompiliere.","",4);
        body.addView(endpoint);body.addView(model);body.addView(token);body.addView(task);
        TextView log=new TextView(a);log.setTextSize(12);log.setTextIsSelectable(true);log.setText("Bereit. Ein lokales Projekt muss geöffnet sein.");
        ScrollView logScroll=new ScrollView(a);logScroll.addView(log);
        body.addView(logScroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout buttons=new LinearLayout(a);buttons.setGravity(Gravity.END);
        Button start=new Button(a);start.setText("START");
        Button cancel=new Button(a);cancel.setText("STOPP");
        buttons.addView(start);buttons.addView(cancel);body.addView(buttons);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("KR Autonomous Agent").setView(body).setPositiveButton("Schließen",(d,w)->{}).create();
        dialog.setOnShowListener(d->dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        cancel.setOnClickListener(v->agent.cancel());
        start.setOnClickListener(v->{
            if(agent.isRunning()){Toast.makeText(a,"Agent läuft bereits",Toast.LENGTH_SHORT).show();return;}
            String url=endpoint.getText().toString().trim();String modelId=model.getText().toString().trim();
            if(url.isEmpty()||modelId.isEmpty()){Toast.makeText(a,"Endpunkt und Modell fehlen",Toast.LENGTH_LONG).show();return;}
            prefs.edit().putString("endpoint",url).putString("model",modelId).apply();
            log.setText("Agentenauftrag gestartet.\n");
            KrAgentCore.Settings settings=new KrAgentCore.Settings(url,modelId,token.getText().toString(),20);
            token.setText("");
            agent.start(task.getText().toString(),settings,(title,detail,respond)->{
                if(a.isFinishing()||a.isDestroyed()){respond.accept(false);return;}
                new AlertDialog.Builder(a).setTitle(title).setMessage(detail)
                  .setNegativeButton("Ablehnen",(d,w)->respond.accept(false))
                  .setPositiveButton("Genehmigen",(d,w)->respond.accept(true))
                  .setOnCancelListener(d->respond.accept(false)).show();
            },new KrAgentCore.Events(){
                public void onEvent(String message){log.append("\n"+message+"\n");logScroll.post(()->logScroll.fullScroll(ScrollView.FOCUS_DOWN));}
                public void onFinished(String status){log.append("\n"+status+"\n");}
            });
        });
        dialog.show();
    }
}
