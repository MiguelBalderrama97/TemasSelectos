package com.tec2.temasselectos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OnImageSavedListener;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.PermissionChecker;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmark;
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmarkDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements Executor {
    private static final int IMAGE_VIDEO_INTENT = 333;
    private static final int ACCES_STORAGE_PERMISSION_REQUEST = 133;
    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private FloatingActionButton btnCapture;
    private TextView txtResultados;
    private Button btnFB;

    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.view_finder);
        txtResultados = findViewById(R.id.txtResultado);
        btnCapture = findViewById(R.id.fabTomarFoto);
        btnFB = findViewById(R.id.button3);
        View btnGaleria = findViewById(R.id.btnGaleria);
        btnGaleria.setOnClickListener(view -> abrirGaleria());

        printKeyHash();

        //Si tenemos los permisos de camara y almacenamiento activamos la camara
        //si no, los solicitamos al usuario
        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        btnFB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                Intent inFace = new Intent(MainActivity.this, FacebookActivity.class);
                bundle.putString("text", txtResultados.getText().toString());
                bundle.putString("uri", uri+"");
                inFace.putExtras(bundle);
                startActivity(inFace);
            }
        });
    }

    private void printKeyHash() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo("com.tec2.temasselectos",PackageManager.GET_SIGNATURES);
            for(Signature signature: info.signatures){
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("KeyHash", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {


        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        //Antes de usar la camara hay que asegurarnos que ningun otro proceso
        //del dispositvo la este usando con la siguiente linea
        CameraX.unbindAll();

        //Definimos los parametros para la captura de imagenes
        //por la camara y de la vista previa
        Size screen = new Size(textureView.getWidth(), textureView.getHeight()); //size of the screen
        PreviewConfig.Builder builder = new PreviewConfig.Builder();
        builder.setTargetResolution(screen);
        PreviewConfig pConfig = builder.build();
        Preview preview = new Preview(pConfig);

        //Le damos al preview de la camara las actualizaciones
        //que recibe del sensor fotografico
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    ViewGroup parent = (ViewGroup) textureView.getParent();
                    parent.removeView(textureView);
                    parent.addView(textureView, 0);
                    textureView.setSurfaceTexture(output.getSurfaceTexture());
                    updateTransform();
                });

        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();
        final ImageCapture imgCap = new ImageCapture(imageCaptureConfig);

        //Cuando presionamos el boton de capturar llamamos al metodo
        //takePicture, el cual guardara la fotografia captada por la camara
        btnCapture.setOnClickListener(v -> {
            File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".png");
            imgCap.takePicture(file, this, new OnImageSavedListener() {

                //La imagen tomada se ha guardado correctamente
                //y la podemos pasar al analizador
                @Override
                public void onImageSaved(@NonNull File file) {
                    analyzeImage(file);
                }

                //Ocurrio un error al guardar la imagen
                @Override
                public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                    if (cause != null) {
                        cause.printStackTrace();
                    }
                }
            });
        });
        CameraX.bindToLifecycle(this, preview, imgCap);
    }

    private void analyzeImage(File file) {
        try {
            //Creamos el analizador y le pasamos el contexto y la uri de la imagen
            FirebaseVisionImage image = FirebaseVisionImage.fromFilePath(getApplicationContext(), Uri.fromFile(file));
            FirebaseVisionCloudLandmarkDetector detector = FirebaseVision.getInstance()
                    .getVisionCloudLandmarkDetector();

            //La tarea result nos debobera de manera asyncrona cuando termine de analizar
            //las imagenes una lista con los resultados probables o de lo contrario un error
            Task<List<FirebaseVisionCloudLandmark>> result = detector.detectInImage(image)
                    .addOnSuccessListener(firebaseVisionCloudLandmarks -> {
                        // Task completed successfully
                        txtResultados.setText("");
                        if (firebaseVisionCloudLandmarks.isEmpty()) {
                            txtResultados.setText("Sin Resultados");
                        } else {
                            for (FirebaseVisionCloudLandmark place : firebaseVisionCloudLandmarks) {
                                txtResultados.append(place.getLandmark() + ",");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Task failed with an exception
                        Log.d("Error", "Ocurrio un error procesando la imagen. " + e.getMessage());
                    });
        } catch (IOException e) {
            Log.d("ERROR", "Error: " + e.toString());
            e.printStackTrace();
        }

    }

    //Metodo para la vista previa de la camara,
    //se encarga de la orientacion de la vista
    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) textureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        textureView.setTransform(mx);
    }


    //Metodo encargado de realizar las comprobaciones y abrir la galeria
    //del dispositvo, al final nos retorna una Uri que es la que pasamos al analizador
    private void abrirGaleria() {
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PermissionChecker.PERMISSION_GRANTED) {
            Log.d("PERMISOS", "PERMISO GARANTIZADO");
            Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            photoPickerIntent.setType("*/*");
            photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
            photoPickerIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*"});
            startActivityForResult(photoPickerIntent, IMAGE_VIDEO_INTENT);
        } else {
            Log.d("PERMISOS", "PERMISO NO GARANTIZADO");
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Es necesario otorgar el permiso")
                            .setMessage("Acepta el permiso de acceso al almacenamiento")
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                                intent.setData(uri);
                                this.startActivity(intent);
                            })
                            .setNegativeButton(android.R.string.no, (dialog, which) -> {
                            })
                            .show();
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            ACCES_STORAGE_PERMISSION_REQUEST);
                }
            }
        }
    }

    //Una vez seleccionada la imagen de la galeria la recuperamos en este metodo
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_VIDEO_INTENT) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    uri = data.getData();
                    Log.d("RESULTADOS", "Imagen Seleccionada: " + uri);
                    // Pasamos la URI al analizador
                    analyzeImage(new File(FileUtils.getRealPath(getApplicationContext(), uri)));
                }


            }
        } else if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permiso no concedido por el usuario.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    //Metodo para comprobar si disponemos de todos los permisos necesarios
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void execute(Runnable runnable) {
        runnable.run();
    }
}
