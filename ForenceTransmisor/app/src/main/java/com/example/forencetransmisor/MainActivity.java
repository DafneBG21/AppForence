package com.example.forencetransmisor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText recipientEmail;
    private Button sendButton;

    // Configuración con repositorio GitHub
    private static final String APK_NAME = "AFLogical-OSE_1.5.2-1.apk";
    private static final String GITHUB_USER = "DafneBG21";
    private static final String REPO_NAME = "AppForence";
    private static final String BRANCH = "main";
    private static final String DOWNLOAD_URL =
            "https://github.com/" + GITHUB_USER + "/" + REPO_NAME + "/raw/" + BRANCH + "/" + APK_NAME;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

         recipientEmail = findViewById(R.id.recipient_email);
        sendButton = findViewById(R.id.send_button);

        sendButton.setOnClickListener(v -> sendDownloadInstructions());
    }

    private void sendDownloadInstructions() {
        String email = recipientEmail.getText().toString().trim();
        if (email.isEmpty()) {
            recipientEmail.setError("Ingrese un correo válido");
            return;
        }

        try {
            String emailText = "¡Análisis forense!\n\n" +
                    "Para instalar la aplicación necesaria, sigue estos pasos:\n\n" +
                    "1. Abre este enlace desde tu dispositivo Android:\n" +
                    DOWNLOAD_URL + "\n\n" +
                    "2. Espera a que se complete la descarga\n" +
                    "3. Toca el archivo descargado para comenzar la instalación\n\n" +
                    "Si tu dispositivo bloquea la instalación:\n" +
                    "- Ve a Ajustes > Seguridad\n" +
                    "- Habilita 'Fuentes desconocidas'\n" +
                    "- Vuelve a intentar la instalación\n\n" +
                    "La aplicación se ejecutará automáticamente después de la instalación.\n\n";

            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:")); // Solo aplicaciones de email
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Instalador Forense - " + APK_NAME);
            emailIntent.putExtra(Intent.EXTRA_TEXT, emailText);


            if (emailIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(emailIntent);
            } else {

                Toast.makeText(this, "No hay aplicaciones de correo instaladas", Toast.LENGTH_LONG).show();

                // Opción para abrir en navegador
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://mail.google.com/mail/?view=cm&fs=1&to=" + email +
                                "&su=Instalador%20Forense%20-%20" + APK_NAME +
                                "&body=" + Uri.encode(emailText)));
                startActivity(browserIntent);
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}