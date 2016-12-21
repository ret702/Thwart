package ret.twart;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static android.content.ContentValues.TAG;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class MainActivity extends AppCompatActivity {
    String mPathScreenShots;
    private BroadcastReceiver receiver;
    FileObserver mFileObserver;
    private StorageReference mStorageRef;
    String mPathPictures;
    private boolean fileStatus = false;

    private static final int READ_REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStorageRef = FirebaseStorage.getInstance().getReference();

        IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_MEDIA_SCANNER_SCAN_FILE");
        filter.addAction("MEDIA_SCANNER_FINISHED");


        mPathPictures = Environment.getExternalStorageDirectory()
                + File.separator + Environment.DIRECTORY_PICTURES + File.separator;

        mPathScreenShots = mPathPictures + "Screenshots" + File.separator;


        mFileObserver = new FileObserver(mPathScreenShots, FileObserver.CREATE) {
            @Override
            public void onEvent(int event, final String path) {
                Log.d(TAG, event + " " + path);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        scanQRImage(path);

                    }
                }).start();

            }
        };

        mFileObserver.startWatching();


        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                saveScreenshot();
            }
        });


        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performFileSearch();
            }
        });


    }


    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    public void performFileSearch() {

        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
        // browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Filter to show only images, using the image MIME data type.
        // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
        // To search for all documents available via installed storage providers,
        // it would be "*/*".
        intent.setType("image/*");

        startActivityForResult(intent, READ_REQUEST_CODE);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().

            if (resultData != null) {
                final Uri URI = resultData.getData();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Bitmap bMap = getBitmapFromUri(URI);
                            File imageFile = saveBitmap(bMap, UUID.randomUUID().toString(), mPathPictures);
                            if (getFileStatus()) {
                                uploadFile(imageFile);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }).start();

            }
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

    }

    private void uploadFile(File file) {
        Uri fileUri = Uri.fromFile(file);

        //eventually store by user ID?
        StorageReference riversRef = mStorageRef.child("images/" + file.getName());


        riversRef.putFile(fileUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        final Uri downloadUrl = taskSnapshot.getDownloadUrl();

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Bitmap bmp = encodeAsBitmap(downloadUrl.toString());
                                    if (getFileStatus()) {
                                        saveBitmap(bmp, UUID.randomUUID().toString(), mPathPictures);
                                    }

                                    ((ImageView) findViewById(R.id.imageView)).setImageBitmap(bmp);
                                } catch (Exception ex) {
                                    setFileStatus(false);
                                }
                            }
                        }).start();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.d(TAG, exception.getMessage());
                        setFileStatus(false);
                        // ...
                    }
                });

    }


    public void saveScreenshot() {
        View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        Bitmap bitmap = getScreenShot(rootView);
        saveBitmap(bitmap, UUID.randomUUID().toString(), mPathScreenShots);
    }

    public static Bitmap getScreenShot(View view) {
        View screenView = view.getRootView();
        screenView.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
        screenView.setDrawingCacheEnabled(false);
        return bitmap;
    }


    Bitmap encodeAsBitmap(String str) throws WriterException {
        BitMatrix result;
        Bitmap bitmap;
        try {
            result = new MultiFormatWriter().encode(str,
                    BarcodeFormat.QR_CODE, 500, 500, null);

            int w = result.getWidth();
            int h = result.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
                }
            }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, 500, 0, 0, w, h);
            setFileStatus(true);
        } catch (IllegalArgumentException iae) {
            setFileStatus(false);
            return null;
        }


        if (bitmap != null) {
            setFileStatus(true);
        } else {
            setFileStatus(false);
        }

        return bitmap;
    }


    public String scanQRImage(String path) {

        Map<EncodeHintType, ErrorCorrectionLevel> hintMap = new HashMap<EncodeHintType, ErrorCorrectionLevel>();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        Bitmap bMap = BitmapFactory.decodeFile(mPathScreenShots + path, bmOptions);


        String contents = null;

        int[] intArray = new int[bMap.getWidth() * bMap.getHeight()];
        //copy pixel data from the Bitmap into the 'intArray' array
        bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());


        LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Reader reader = new MultiFormatReader();
        try {
            Result result = reader.decode(bitmap);
            contents = result.getText();
            final String testString = contents;


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.textView)).setText(testString);
                }
            });


        } catch (Exception e) {
            Log.e("QrTest", "Error decoding barcode", e);
        }


        return contents;
    }


    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    public File saveBitmap(Bitmap bm, String fileName, String path) {

        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(path, fileName + ".jpg");
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.flush();
            fOut.close();

            setFileStatus(true);

        } catch (Exception e) {
            setFileStatus(false);
            e.printStackTrace();
        }

        return file;
    }


    public boolean getFileStatus() {
        return fileStatus;
    }

    public void setFileStatus(boolean fileStatus) {
        this.fileStatus = fileStatus;
    }
}


