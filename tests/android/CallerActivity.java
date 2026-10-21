package org.li.maprelay.fixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
public final class CallerActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); receive(getIntent()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);setIntent(intent);receive(intent); }
    private void receive(Intent intent) {
        StringBuilder proof=new StringBuilder();
        for (String pkg:new String[]{"com.autonavi.minimap","com.baidu.BaiduMap","com.tencent.map"}) {
            try { getPackageManager().getPackageInfo(pkg,0);proof.append(pkg).append("=detected\n"); }
            catch (Exception ex) { throw new RuntimeException("Compatibility package not visible: "+pkg,ex); }
        }
        try {
            FileOutputStream output=new FileOutputStream(new File(getFilesDir(),"detected.txt"));output.write(proof.toString().getBytes("UTF-8"));output.close();
        } catch (Exception ex) { throw new RuntimeException(ex); }
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(intent.getStringExtra("uri"))));finish();
    }
}
