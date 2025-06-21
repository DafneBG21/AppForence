package com.example.forencereceptor;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int FOLDER_PICKER_CODE = 123;
    private Uri folderUri;
    private ProgressBar progressBar;
    private TextView statusText;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<Uri> fileUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectBtn = findViewById(R.id.select_folder_button);
        Button sendBtn = findViewById(R.id.send_data_button);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);

        selectBtn.setOnClickListener(v -> openFolderPicker());
        sendBtn.setOnClickListener(v -> {
            if (!fileUris.isEmpty()) {
                new ZipTask().execute();
            } else {
                showToast("No hay archivos para enviar");
            }
        });
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, FOLDER_PICKER_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FOLDER_PICKER_CODE && resultCode == RESULT_OK && data != null) {
            folderUri = data.getData();
            getContentResolver().takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            scanFolderForFiles();
        }
    }

    private void scanFolderForFiles() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Buscando archivos...");

        new Thread(() -> {
            fileUris.clear();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
            );

            try (Cursor cursor = getContentResolver().query(
                    childrenUri,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String docId = cursor.getString(0);
                        String name = cursor.getString(1);

                        if (!name.startsWith(".")) { // Ignorar archivos ocultos
                            Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(
                                    folderUri, docId);
                            fileUris.add(fileUri);
                        }
                    }
                }

                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Archivos encontrados: " + fileUris.size());
                    showToast("Se encontraron " + fileUris.size() + " archivos");
                });

            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Error al leer archivos");
                    showToast("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private class ZipTask {
        void execute() {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setMax(fileUris.size());

            new Thread(() -> {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File zipFile = new File(getExternalCacheDir(), "forensic_data_" + timeStamp + ".zip");

                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                    int count = 0;

                    for (Uri fileUri : fileUris) {
                        String fileName = getFileName(fileUri);
                        if (fileName != null) {
                            try (InputStream in = getContentResolver().openInputStream(fileUri)) {
                                ZipEntry entry = new ZipEntry(fileName);
                                zos.putNextEntry(entry);

                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = in.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                }
                                zos.closeEntry();

                                count++;
                                int progress = count;
                                handler.post(() -> {
                                    progressBar.setProgress(progress);
                                    statusText.setText("Comprimiendo " + progress + "/" + fileUris.size());
                                });
                            }
                        }
                    }

                    int finalCount = count;
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        showToast("ZIP creado con " + finalCount + " archivos");
                        shareZipFile(zipFile, finalCount);
                    });

                } catch (IOException e) {
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        showToast("Error al crear ZIP: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        }
        return null;
    }

    private void shareZipFile(File zipFile, int fileCount) {
        Uri contentUri = FileProvider.getUriForFile(
                this,
                "com.example.forencereceptor.fileprovider",
                zipFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/zip");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Datos Forenses - " + fileCount + " archivos");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Contiene " + fileCount + " archivos recolectados");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, "Enviar datos via"));
        showToast("Selecciona la app para enviar el archivo ZIP");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}