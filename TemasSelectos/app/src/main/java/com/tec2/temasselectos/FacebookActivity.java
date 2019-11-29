package com.tec2.temasselectos;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.share.Sharer;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class FacebookActivity extends AppCompatActivity {

    private ImageView image;
    private TextView text;
    private Button btnConfirm;

    private Bundle bundle;

    private String place;
    private Uri uri;

    private CallbackManager callbackManager;
    private ShareDialog shareDialog;

    private ClipboardManager clipboard;
    private ClipData clip;

    private Target target = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            SharePhoto sharePhoto = new SharePhoto.Builder().setBitmap(bitmap).build();

            if (ShareDialog.canShow(SharePhotoContent.class)) {
                SharePhotoContent content = new SharePhotoContent.Builder().addPhoto(sharePhoto).build();
                shareDialog.show(content);
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {

        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(FacebookActivity.this);
        setContentView(R.layout.activity_facebook);

        image = findViewById(R.id.imgFB);
        text = findViewById(R.id.txtFB);
        btnConfirm = findViewById(R.id.btnConfirm);

        recoverAndSetData();

        callbackManager = CallbackManager.Factory.create();
        shareDialog = new ShareDialog(this);

        btnConfirm.setOnClickListener((View view) -> {
                    copyToClipBoard();

                    Toast.makeText(FacebookActivity.this, "Location copied to Clip Board ", Toast.LENGTH_LONG).show();

                    shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
                        @Override
                        public void onSuccess(Sharer.Result result) {
                            Toast.makeText(FacebookActivity.this, "Shared Successful", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onCancel() {
                            Toast.makeText(FacebookActivity.this, "Shared Cancelled", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(FacebookException error) {
                            Toast.makeText(FacebookActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    Picasso.with(getApplicationContext()).load(uri).into(target);
                }
        );

    }

    /**
     * Get Bundle and safe the information
     */
    private void recoverAndSetData() {
        bundle = getIntent().getExtras();

        place = bundle.getString("text");
        uri = Uri.parse(bundle.getString("uri"));

        image.setImageURI(uri);
        text.setText(place);
    }

    /**
     * Copy location to ClipBoard
     */
    private void copyToClipBoard() {
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clip = ClipData.newPlainText(text + "", place);
        clipboard.setPrimaryClip(clip);
    }

}
