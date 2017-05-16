package edu.firstapplication;

import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.provider.ContactsContract;
import android.renderscript.RenderScript;
import android.support.annotation.IntegerRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

import messagepack.ParamUnpacker;
import network.CNNdroid;

import static android.graphics.Color.blue;
import static android.graphics.Color.green;
import static android.graphics.Color.red;

public class MainActivity extends AppCompatActivity {

    ImageView imageView;
    TextView textView;
    Button startAsyButton;
    Button computeButton;
    RenderScript myRenderScript;
    CNNdroid myConv = null;
    String[] labels;
    long loadTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView) findViewById(R.id.testImageView);
        textView = (TextView) findViewById(R.id.testTextView);
        startAsyButton = (Button) findViewById(R.id.startAsyButton);
        computeButton = (Button) findViewById(R.id.computeButton);

        myRenderScript = RenderScript.create(this);
        readLabels();

        Bitmap img = getImgRes("bird");
        imageView.setImageBitmap(img);

        startAsyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prepareModel asyncTask = new prepareModel();
                asyncTask.execute();
            }
        });

        computeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap img = getImgRes("bird");
                Bitmap scaled = Bitmap.createScaledBitmap(img, 32, 32 ,false);

                ParamUnpacker pu = new ParamUnpacker();
                float[][][] mean = (float[][][]) pu.unpackerFunction("/sdcard/Data_Cifar10/mean.msg", float[][][].class);

                float[][][][] inputBatch = new float[1][3][32][32];
                for (int j = 0; j < 32; ++j)
                    for (int k = 0; k < 32; ++k) {
                        int color = scaled.getPixel(j, k);
                        inputBatch[0][0][k][j] = (float) (blue(color)) - mean[0][j][k];
                        inputBatch[0][1][k][j] = (float) (green(color)) - mean[1][j][k];
                        inputBatch[0][2][k][j] = (float) (red(color)) - mean[2][j][k];
                    }

                float[][] output = (float[][]) myConv.compute(inputBatch);
                textView.setText(accuracy(output[0], labels, 3));
            }
        });
    }

    private void readLabels() {
        labels = new String[1000];
        File f = new File("/sdcard/Data_Cifar10/labels.txt");
        Scanner s = null;
        int iter = 0;

        try {
            s = new Scanner(f);
            while (s.hasNextLine()) {
                String str = s.nextLine();
                labels[iter++] = str;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String accuracy(float[] input_matrix, String[] labels, int topk) {
        String result = "";
        int[] max_num = {-1, -1, -1, -1, -1};
        float[] max = new float[topk];
        for (int k = 0; k < topk ; ++k) {
            for (int i = 0; i < 10; ++i) {
                if (input_matrix[i] > max[k]) {
                    boolean newVal = true;
                    for (int j = 0; j < topk; ++j)
                        if (i == max_num[j])
                            newVal = false;
                    if (newVal) {
                        max[k] = input_matrix[i];
                        max_num[k] = i;
                    }
                }
            }
        }

        for (int i = 0 ; i < topk ; i++)
            result += labels[max_num[i]]  + " , P = " + max[i] * 100 + " %\n\n";
        return result;
    }

    public Bitmap getImgRes(String name) {
        ApplicationInfo appInfo = getApplicationInfo();
        int resID = getResources().getIdentifier(name, "drawable", appInfo.packageName);
        return BitmapFactory.decodeResource(getResources(), resID);
    }

    private class prepareModel extends AsyncTask<RenderScript, Void, CNNdroid> {

        @Override
        protected void onPreExecute() {
            textView.setText("加载网络模型参数中");
        }

        @Override
        protected CNNdroid doInBackground(RenderScript... params) {
            loadTime = System.currentTimeMillis();
            try {
                myConv = new CNNdroid(myRenderScript, "/sdcard/Data_Cifar10/Cifar10_def.txt");
            } catch (Exception e) {
                e.printStackTrace();
            }
            loadTime = System.currentTimeMillis() - loadTime;
            return myConv;
        }

        @Override
        protected void onPostExecute(CNNdroid model) {
            textView.setText("加载结束");
        }
    }
}
