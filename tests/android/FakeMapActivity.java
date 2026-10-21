package org.li.maprelay.fixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
public final class FakeMapActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); receive(getIntent()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);setIntent(intent);receive(intent); }
    private void receive(Intent intent) {
        String uri=Intent.ACTION_SEND.equals(intent.getAction())?intent.getStringExtra(Intent.EXTRA_TEXT):String.valueOf(intent.getData());
        try {
            FileOutputStream output=new FileOutputStream(new File(getFilesDir(),"received.txt"));
            output.write(uri.getBytes("UTF-8"));output.close();
        } catch (Exception ex) { throw new RuntimeException(ex); }
        TextView view=new TextView(this);view.setText("TEST MAP RECEIVER\n\n"+getPackageName()+"\n\n"+uri);view.setTextSize(18);view.setPadding(40,100,40,40);setContentView(view);
    }
}
