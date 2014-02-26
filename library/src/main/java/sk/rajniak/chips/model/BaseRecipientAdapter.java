package sk.rajniak.chips.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import sk.rajniak.chips.R;

public abstract class BaseRecipientAdapter extends BaseAdapter implements Filterable {
    private static final String TAG = BaseRecipientAdapter.class.getSimpleName();

    private static final boolean DEBUG = false;

    /**
     * The preferred number of results to be retrieved. This number may be
     * exceeded if there are several directories configured, because we will use
     * the same limit for all directories.
     */
    private static final int DEFAULT_PREFERRED_MAX_RESULT_COUNT = 10;

    private final LayoutInflater mInflater;

    private List<RecipientEntry> mEntries;

    private EntriesUpdatedObserver mEntriesUpdatedObserver;

    public BaseRecipientAdapter(Context context) {
        this(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT);
    }

    public BaseRecipientAdapter(Context context, int preferredMaxResultCount) {
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        final List<RecipientEntry> entries = getEntries();
        return entries != null ? entries.size() : 0;
    }

    @Override
    public RecipientEntry getItem(int position) {
        return getEntries().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return RecipientEntry.ENTRY_TYPE_SIZE;
    }

    @Override
    public int getItemViewType(int position) {
        return getEntries().get(position).getEntryType();
    }

    @Override
    public boolean isEnabled(int position) {
        return getEntries().get(position).isSelectable();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final RecipientEntry entry = getEntries().get(position);
        String displayName = entry.getDisplayName();
        String destination = entry.getDestination();
        if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, destination)) {
            displayName = destination;

            // We only show the destination for secondary entries, so clear it
            // only for the first level.
            if (entry.isFirstLevel()) {
                destination = null;
            }
        }

        final View itemView = convertView != null ? convertView : mInflater.inflate(
                getItemLayout(), parent, false);
        final TextView displayNameView = (TextView) itemView.findViewById(getDisplayNameId());
        final TextView destinationView = (TextView) itemView.findViewById(getDestinationId());
        final TextView destinationTypeView = (TextView) itemView
                .findViewById(getDestinationTypeId());
        final ImageView imageView = (ImageView) itemView.findViewById(getPhotoId());
        displayNameView.setText(displayName);
        if (!TextUtils.isEmpty(destination)) {
            destinationView.setText(destination);
        } else {
            destinationView.setText(null);
        }
        if (destinationTypeView != null) {
//            final CharSequence destinationType = mQuery
//                    .getTypeLabel(mContext.getResources(), entry.getDestinationType(),
//                            entry.getDestinationLabel()).toString().toUpperCase();
//
//            destinationTypeView.setText(destinationType);
        }

        if (entry.isFirstLevel()) {
            displayNameView.setVisibility(View.VISIBLE);
            if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);
                final byte[] photoBytes = entry.getPhotoBytes();
                if (photoBytes != null) {
                    final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                            photoBytes.length);
                    imageView.setImageBitmap(photo);
                } else {
                    imageView.setImageResource(getDefaultPhotoResource());
                }
            }
        } else {
            displayNameView.setVisibility(View.GONE);
            if (imageView != null) {
                imageView.setVisibility(View.INVISIBLE);
            }
        }
        return itemView;
    }

    @Override
    public Filter getFilter() {
        return new DefaultFilter();
    }

    public abstract List<RecipientEntry> getAlternativeRecipients(String displayName);

    public abstract HashMap<String, List<RecipientEntry>> getAlternativeRecipients(HashSet<String> displayNames);

    protected abstract HashMap<String, List<RecipientEntry>> getMatchingRecipients(CharSequence constraint);

    public interface EntriesUpdatedObserver {
        public void onChanged(List<RecipientEntry> entries);
    }

    public void registerUpdateObserver(EntriesUpdatedObserver observer) {
        mEntriesUpdatedObserver = observer;
    }

    /** Resets {@link #mEntries} and notify the event to its parent ListView. */
    private void updateEntries(List<RecipientEntry> newEntries) {
        mEntries = newEntries;
        mEntriesUpdatedObserver.onChanged(newEntries);
        notifyDataSetChanged();
    }

    protected List<RecipientEntry> getEntries() {
        return mEntries;
    }

    /**
     * Returns a layout id for each item inside auto-complete list.
     *
     * Each View must contain two TextViews (for display name and destination) and one ImageView
     * (for photo). Ids for those should be available via {@link #getDisplayNameId()},
     * {@link #getDestinationId()}, and {@link #getPhotoId()}.
     */
    protected int getItemLayout() {
        return R.layout.chips_recipient_dropdown_item;
    }

    /**
     * Returns a resource ID representing an image which should be shown when ther's no relevant
     * photo is available.
     */
    protected int getDefaultPhotoResource() {
        return R.drawable.ic_contact_picture;
    }

    /**
     * Returns an id for TextView in an item View for showing a display name. By default
     * {@link android.R.id#title} is returned.
     */
    protected int getDisplayNameId() {
        return android.R.id.title;
    }

    /**
     * Returns an id for TextView in an item View for showing a destination
     * (an email address or a phone number).
     * By default {@link android.R.id#text1} is returned.
     */
    protected int getDestinationId() {
        return android.R.id.text1;
    }

    /**
     * Returns an id for TextView in an item View for showing the type of the destination.
     * By default {@link android.R.id#text2} is returned.
     */
    protected int getDestinationTypeId() {
        return android.R.id.text2;
    }

    /**
     * Returns an id for ImageView in an item View for showing photo image for a person. In default
     * {@link android.R.id#icon} is returned.
     */
    protected int getPhotoId() {
        return android.R.id.icon;
    }

    /**
     * An asynchronous filter used for loading two data sets: email rows from the local
     * contact provider and the list of {@link android.provider.ContactsContract.Directory}'s.
     */
    private final class DefaultFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (DEBUG) {
                Log.d(TAG, "start filtering. constraint: " + constraint + ", thread:"
                        + Thread.currentThread());
            }

            final FilterResults results = new FilterResults();

            if (TextUtils.isEmpty(constraint)) {
                // Return empty results.
                return results;
            }

            results.values = getMatchingRecipients(constraint);
            results.count = 1;

            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            if (results.values != null) {
                HashMap<String, List<RecipientEntry>> defaultFilterResult
                        = (HashMap<String, List<RecipientEntry>>) results.values;

                List<RecipientEntry> resultList = new ArrayList<>();
                for (RecipientEntry entry : defaultFilterResult.get(constraint.toString())) {
                    resultList.add(entry);
                }

                updateEntries(resultList);
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            final RecipientEntry entry = (RecipientEntry)resultValue;
            final String displayName = entry.getDisplayName();
            final String emailAddress = entry.getDestination();
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, emailAddress)) {
                return emailAddress;
            } else {
                return new Rfc822Token(displayName, emailAddress, null).toString();
            }
        }
    }
}
