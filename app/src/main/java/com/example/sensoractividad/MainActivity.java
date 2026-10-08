package com.example.sensoractividad;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements SensorEventListener {

    private static final String URL_IMAGEN =
            "https://www.gstatic.com/webp/gallery/1.jpg";

    private TextView tvSensor;
    private TextView tvEstado;
    private ImageView imgFoto;
    private Button btnThread;
    private Button btnAsync;

    private SensorManager sensorManager;
    private Sensor sensorRotacion;

    private Thread hiloDescarga;
    private DescargarAsync tareaAsync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSensor = findViewById(R.id.tvSensor);
        tvEstado = findViewById(R.id.tvEstado);
        imgFoto = findViewById(R.id.imgFoto);
        btnThread = findViewById(R.id.btnThread);
        btnAsync = findViewById(R.id.btnAsync);

        // Obtener el sensor solicitado en la pauta.
        sensorManager =
                (SensorManager) getSystemService(SENSOR_SERVICE);

        if (sensorManager != null) {
            sensorRotacion = sensorManager.getDefaultSensor(
                    Sensor.TYPE_ROTATION_VECTOR
            );
        }

        if (sensorRotacion == null) {
            tvSensor.setText(
                    "Este dispositivo no tiene sensor de vector de rotación."
            );
        }

        btnThread.setOnClickListener(view -> descargarConThread());

        btnAsync.setOnClickListener(view -> {
            tareaAsync = new DescargarAsync();
            tareaAsync.execute();
        });
    }

    private void iniciarDescarga(String metodo) {
        tvEstado.setText("Descargando con " + metodo + "...");
        imgFoto.setImageDrawable(null);

        // Evita iniciar varias descargas al mismo tiempo.
        btnThread.setEnabled(false);
        btnAsync.setEnabled(false);
    }

    private void mostrarResultado(Bitmap imagen, String metodo) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        btnThread.setEnabled(true);
        btnAsync.setEnabled(true);

        if (imagen != null) {
            imgFoto.setImageBitmap(imagen);
            tvEstado.setText("Imagen descargada con " + metodo);
        } else {
            tvEstado.setText(
                    "No se pudo descargar. Revisa Internet y la URL."
            );
        }
    }

    // Primera forma: Thread y post para actualizar la interfaz.
    private void descargarConThread() {
        iniciarDescarga("Thread");

        hiloDescarga = new Thread(() -> {
            Bitmap imagen = descargarImagen();

            if (!Thread.currentThread().isInterrupted()) {
                imgFoto.post(() -> mostrarResultado(imagen, "Thread"));
            }
        });

        hiloDescarga.start();
    }

    // Método compartido por Thread y AsyncTask.
    // Se ejecuta siempre en segundo plano.
    private Bitmap descargarImagen() {
        HttpURLConnection conexion = null;

        try {
            URL url = new URL(URL_IMAGEN);
            conexion = (HttpURLConnection) url.openConnection();
            conexion.setConnectTimeout(10000);
            conexion.setReadTimeout(10000);
            conexion.connect();

            if (conexion.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            try (InputStream entrada = conexion.getInputStream()) {
                return BitmapFactory.decodeStream(entrada);
            }

        } catch (Exception e) {
            return null;

        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    // Segunda forma: AsyncTask, incluida porque la pauta la pide.
    @SuppressWarnings("deprecation")
    private class DescargarAsync extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected void onPreExecute() {
            iniciarDescarga("AsyncTask");
        }

        @Override
        protected Bitmap doInBackground(Void... parametros) {
            return descargarImagen();
        }

        @Override
        protected void onPostExecute(Bitmap imagen) {
            mostrarResultado(imagen, "AsyncTask");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sensorRotacion != null) {
            boolean registrado = sensorManager.registerListener(
                    this,
                    sensorRotacion,
                    SensorManager.SENSOR_DELAY_UI
            );

            if (!registrado) {
                tvSensor.setText("No se pudo activar el sensor.");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent evento) {
        if (evento.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float x = evento.values[0];
            float y = evento.values[1];
            float z = evento.values[2];

            tvSensor.setText(String.format(
                    Locale.getDefault(),
                    "Componentes del vector:\nX: %.3f\nY: %.3f\nZ: %.3f",
                    x, y, z
            ));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int precision) {
        // No necesitamos realizar acciones para esta actividad.
    }

    @Override
    protected void onDestroy() {
        if (hiloDescarga != null) {
            hiloDescarga.interrupt();
        }

        if (tareaAsync != null) {
            tareaAsync.cancel(true);
        }

        super.onDestroy();
    }
}