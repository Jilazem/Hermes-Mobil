package com.hermes.sharesender;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Denetmen MS-3 kaniti: content:// gercek icerik saglayicidan (FileProvider)
 * paylasim. adb root'un kabuk grant'i MediaStore'da reddediliyordu (gonderen
 * UID saglayici izni tasimiyor); gercek senaryoda gonderen uygulama URI'yi
 * KENDI saglayicisindan grant'lar — bu app tam olarak onu yapar:
 * dosyayi files/share/'e yazar, FileProvider URI'si uretir, ACTION_SEND ile
 * + FLAG_GRANT_READ_URI_PERMISSION ile Hermes ShareProxyActivity'ye atar.
 */
public class SendActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String content = getIntent().getStringExtra("content");
        if (content == null) content = "content-provider-kaniti";
        String fname = getIntent().getStringExtra("fname");
        if (fname == null) fname = "content-prova.txt";
        try {
            File dir = new File(getFilesDir(), "share");
            dir.mkdirs();
            File f = new File(dir, fname);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, "com.hermes.sharesender.fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, getIntent().getStringExtra("text"));
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setComponent(new ComponentName(
                "com.hermes.mobile.v2", "com.hermes.mobile.ShareProxyActivity"));
            // NoDisplay temali Activity, startActivity'den ONCE finish()
            // cirmali ("Can not launch activity with Theme.NoDisplay that is
            // not finished first" kurali).
            finish();
            startActivity(send);
        } catch (Exception e) {
            android.util.Log.e("ShareSender", "gönderim hatası", e);
        }
    }
}
