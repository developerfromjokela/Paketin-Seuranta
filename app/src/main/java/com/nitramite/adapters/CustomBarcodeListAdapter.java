package com.nitramite.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.CardView;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.nitramite.paketinseuranta.Parcel;
import com.nitramite.paketinseuranta.R;

import java.util.ArrayList;

public class CustomBarcodeListAdapter extends ArrayAdapter<String> {

    private final Activity context;
    private final ArrayList<String> parcelCodeItems;
    private final ArrayList<String> parcelTitleItems;
    private Integer marginBottom = 5;

    public CustomBarcodeListAdapter(Activity context, ArrayList<String> parcelCodeItems, ArrayList<String> parcelTitleItems, Integer marginBottom) {
        super(context, R.layout.custom_barcodes_list_adapter, parcelCodeItems);
        // TODO Auto-generated constructor stub

        this.context = context;
        this.parcelCodeItems = parcelCodeItems;
        this.parcelTitleItems = parcelTitleItems;
        this.marginBottom = marginBottom;
    }


    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.custom_barcodes_list_adapter, null, true);

        // Find views
        CardView cardView = (CardView) rowView.findViewById(R.id.cardView);
        TextView packageInfoTV = (TextView) rowView.findViewById(R.id.packageInfoTV);
        ImageView barcodeView = (ImageView) rowView.findViewById(R.id.barcodeView);

        // Set package info view
        packageInfoTV.setText(parcelCodeItems.get(position) + " | " + parcelTitleItems.get(position));

        // Get display size param's
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

        // Create code-128 bitmap
        Bitmap bitmap = null;
        try {
            bitmap = Parcel.encodeAsBitmap(parcelCodeItems.get(position), BarcodeFormat.CODE_128, screenWidth, 300);
            barcodeView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }


        // Set normal or custom margins
        float bMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.marginBottom, context.getResources().getDisplayMetrics());
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) cardView.getLayoutParams();
        layoutParams.setMargins(5, 5, 5, (int) bMargin);
        cardView.requestLayout();

        return rowView;
    }


} // END OF CLASS
