package unist.ivader.visnetworkclient2;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CLIENTLOG";

    public EditText ipAddress, processNumber, portNumber;
    public RadioGroup compressRadioGroup;
    public RadioButton compressTrue, compressFalse;
    public Button connectButton, resetButton;
    public TextView console;

    private Runnable networkRunnable = new NetworkRunnable();
    private Thread networkThread = new Thread(networkRunnable);
    private Runnable secondPortRunnable = new SecondPortRunnable();
    private Thread secondPortThread = new Thread(secondPortRunnable);

    String ipAddr = "127.0.0.1";
    int processNum = 10;
    int portNum = 8000;
    boolean compression = false;

    File[] file;
    File[] decompressedFile;
    List<String> savingData = new ArrayList<>(10);
    byte[][] compressedData;
    long connectionStart, connectionEnd, communicationStart, communicationEnd, decompressStart, decompressEnd, saveStart, saveEnd;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipAddress = findViewById(R.id.ip_addr_in);
        processNumber = findViewById(R.id.process_num_in);
        portNumber = findViewById(R.id.port_num_in);
        compressRadioGroup = findViewById(R.id.compress_in);
        compressTrue = findViewById(R.id.compression_true);
        compressFalse = findViewById(R.id.compression_false);
        connectButton = findViewById(R.id.connectButton);
        resetButton = findViewById(R.id.resetButton);
        console = findViewById(R.id.console);

        compressFalse.toggle();
        compressRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i) {
                    case R.id.compression_true:
                        compression = true;
                        break;
                    case R.id.compression_false:
                        compression = false;
                        break;
                }
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                console.setText("Click RESET after use.\n");
                ipAddr = ipAddress.getText().toString();
                processNum = Integer.parseInt(processNumber.getText().toString());
                portNum = Integer.parseInt(portNumber.getText().toString());
                console.append("IP : " + ipAddr + ":" + portNum + "  Process Number : " + processNum + "\n");

                String path = getApplicationContext().getFilesDir().getPath() + "/";
                file = new File[processNum];
                decompressedFile = new File[processNum];

                if (compression) {
                    console.append("Mode : Compression\n");
                    Log.d(TAG, "Mode : Compression");
                    for (int i = 0 ; i < processNum ; i++) {
                        file[i] = new File(path + "csv" + i + ".gz");
                        decompressedFile[i] = new File(path + "csv" + i + ".csv");
                    }
                    Log.d(TAG, "File created.");
                } else {
                    console.append("Mode : Normal\n");
                    Log.d(TAG, "Mode : Normal");
                    for (int i = 0 ; i < processNum ; i++) {
                        file[i] = new File(path + "csv" + i + ".csv");
                        savingData.add(i, "");
                    }
                    Log.d(TAG, "File created.");
                }

                networkThread.start();
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "RESET", Toast.LENGTH_SHORT).show();
                Intent restartIntent = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getBaseContext().getPackageName());
                if (restartIntent != null) {
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                }
                startActivity(restartIntent);
            }
        });
    }

    public void connectServer(final String ipAddr, final int portNum) {
        try {
            final Socket sock = new Socket(ipAddr, portNum);
            Log.d(TAG, "Main server connected.");

            final PrintWriter makeConnection = new PrintWriter(sock.getOutputStream());
            makeConnection.print("Connection set");
            makeConnection.flush();

            InputStream portInput = sock.getInputStream();
            int openPortRead;
            byte[] openPortReadBuffer = new byte[128];
            openPortRead = portInput.read(openPortReadBuffer);
            processNum = Integer.parseInt(new String(openPortReadBuffer, 0, openPortRead, UTF_8));
            Log.d(TAG, "Process number : " + processNum);

            portInput.close();
            makeConnection.close();
            sock.close();

            networkThread.interrupt();
            Log.d(TAG, "Starting parallel port...");
            secondPortThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class NetworkRunnable extends Thread implements Runnable {
        @Override
        public void run() {
            super.run();
            connectServer(ipAddr, portNum);
        }
    }

    public class SecondPortRunnable extends Thread implements Runnable {
        @Override
        public void run() {
            super.run();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final ExecutorService executorService = Executors.newFixedThreadPool(processNum);

                    for (int i = 0 ; i < processNum ; i++) {
                        final Runnable poolRunnable = new Runnable() {
                            @Override
                            public void run() {
                                int threadNumber = Integer.parseInt(Thread.currentThread().getName().substring(14)) - 1;
                                Log.d(TAG, "threadNumber : " + threadNumber);

                                try {
                                    Log.d(TAG, "Waiting for server open...");
                                    sleep(1500);

                                    Socket parallelPort = new Socket(ipAddr, 9000 + threadNumber);
                                    connectionEnd = System.currentTimeMillis();
                                    Log.d(TAG, (9000+threadNumber) + " connected.");

                                    InputStream parallelInput = parallelPort.getInputStream();
                                    int bytesRead;
                                    byte[] contents = new byte[10240];

                                    if (threadNumber == 0) {
                                        communicationStart = System.currentTimeMillis();
                                    }

                                    if (compression) {
                                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                                        compressedData = new byte[processNum][1024576];
                                        Log.d(TAG, "Getting files...");
                                        while ((bytesRead = parallelInput.read(contents)) != -1) {
                                            out.write(contents, 0, bytesRead);
                                            compressedData[threadNumber] = out.toByteArray();
                                        }
                                        communicationEnd = System.currentTimeMillis();

                                        FileOutputStream fos = new FileOutputStream(file[threadNumber]);
                                        fos.write(compressedData[threadNumber]);
                                        fos.close();

                                        GZIPInputStream gin = new GZIPInputStream(new FileInputStream(file[threadNumber]));
                                        FileOutputStream zipout = new FileOutputStream(decompressedFile[threadNumber]);

                                        int count;
                                        byte[] buf = new byte[1024];
                                        Log.d(TAG, "Decompressing...");
                                        if (threadNumber == 0) {
                                            decompressStart = System.currentTimeMillis();
                                        }

                                        while ((count = gin.read(buf, 0, 1024)) != -1) {
                                            zipout.write(buf, 0, count);
                                        }
                                        decompressEnd = System.currentTimeMillis();

                                        zipout.close();
                                        gin.close();
                                    } else {
                                        StringBuilder append = new StringBuilder();
                                        Log.d(TAG, "Getting files...");
                                        while ((bytesRead = parallelInput.read(contents)) != -1) {
                                            String result = new String(contents, 0, bytesRead, UTF_8);
                                            append.append(result);
                                        }
                                        savingData.add(threadNumber, append.toString());
                                        communicationEnd = System.currentTimeMillis();

                                        if (threadNumber == 0) {
                                            saveStart = System.currentTimeMillis();
                                        }
                                        FileWriter writer = new FileWriter(file[threadNumber]);
                                        writer.write(savingData.get(threadNumber));
                                        writer.close();
                                        saveEnd = System.currentTimeMillis();
                                    }

                                    parallelInput.close();
                                    parallelPort.close();
                                } catch (IOException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        executorService.execute(poolRunnable);
                    }

                    // 전송이 모두 완료될 때까지 대기
                    try {
                        do {
                            connectionStart = System.currentTimeMillis();
                            try {
                                if (!executorService.isShutdown()) {
                                    Log.d(TAG, "종료 대기 중...");
                                    executorService.shutdown();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                executorService.shutdownNow();
                            }
                        } while (!executorService.awaitTermination(100, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (compression) {
                        console.append("Connection time : " + (connectionEnd - connectionStart - 12000) + "ms\n");
                        console.append("Communication time : " + (communicationEnd - communicationStart) + "ms\n");
                        console.append("Decompression time : " + (decompressEnd - decompressStart) + "ms\n");
                        Log.d(TAG, "Connection time : " + (connectionEnd - connectionStart - 12000) + "ms");
                        Log.d(TAG, "Communication time : " + (communicationEnd - communicationStart) + "ms");
                        Log.d(TAG, "Decompression time : " + (decompressEnd - decompressStart) + "ms");
                    } else {
                        console.append("Connection time : " + (connectionEnd - connectionStart - 1500) + "ms\n");
                        console.append("Communication time : " + (communicationEnd - communicationStart) + "ms\n");
                        console.append("Saving time : " + (saveEnd - saveStart) + "ms\n");
                        Log.d(TAG, "Connection time : " + (connectionEnd - connectionStart - 1500) + "ms");
                        Log.d(TAG, "Communication time : " + (communicationEnd - communicationStart) + "ms");
                        Log.d(TAG, "Connection time : " + (saveEnd - saveStart) + "ms");
                    }
                    console.append("Download complete.\n");

                }
            });
        }
    }
}
