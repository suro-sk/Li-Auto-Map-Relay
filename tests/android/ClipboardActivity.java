package org.li.maprelay.fixture;
import android.app.Activity;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
public final class ClipboardActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); setContentView(new TextView(this)); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (!focused) return;
        getWindow().getDecorView().postDelayed(new Runnable(){ @Override public void run() {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            String value=clipboard.hasPrimaryClip()?clipboard.getPrimaryClip().getItemAt(0).coerceToText(ClipboardActivity.this).toString():"";
            try {
                FileOutputStream output=new FileOutputStream(new File(getFilesDir(),"received.txt"));
                output.write(value.getBytes("UTF-8")); output.close(); finish();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }},250);
    }
}
