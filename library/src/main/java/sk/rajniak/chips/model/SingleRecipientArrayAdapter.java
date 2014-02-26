package sk.rajniak.chips.model;

import android.content.Context;
import android.text.util.Rfc822Tokenizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class SingleRecipientArrayAdapter extends ArrayAdapter<RecipientEntry> {
    private int mLayoutId;

    private final LayoutInflater mLayoutInflater;

    public SingleRecipientArrayAdapter(Context context, int resourceId, RecipientEntry entry) {
        super(context, resourceId, new RecipientEntry[] {
            entry
        });
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutId = resourceId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = newView();
        }
        bindView(convertView, getItem(position));
        return convertView;
    }

    private View newView() {
        return mLayoutInflater.inflate(mLayoutId, null);
    }

    private static void bindView(View view, RecipientEntry entry) {
        TextView display = (TextView) view.findViewById(android.R.id.title);
        ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        display.setText(entry.getDisplayName());
        display.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.VISIBLE);
        TextView destination = (TextView) view.findViewById(android.R.id.text1);
        destination.setText(Rfc822Tokenizer.tokenize(entry.getDestination())[0].getAddress());
    }
}
