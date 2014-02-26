package sk.rajniak.chips.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sk.rajniak.chips.R;

/**
 * If recipient entry is not generated or fake, then when user clicks on the chip, we give him alternatives.
 * We generate alternatives based on the display name,
 * cause it is possible to have multiple email addresses with the same display name.
 */
public class RecipientAlternatesAdapter extends BaseAdapter {

    private static final String TAG = RecipientAlternatesAdapter.class.getSimpleName().substring(0, 22);

    public static final int MAX_LOOKUPS = 50;

    private final String mDisplayName;

    private final LayoutInflater mInflater;

    private final BaseRecipientAdapter mAdapter;

    private List<RecipientEntry> mEntries;

    private OnCheckedItemChangedListener mCheckedItemChangedListener;

    private int mCheckedItemPosition = -1;

    public interface RecipientMatchCallback {
        public void matchesFound(Map<String, RecipientEntry> results);
        /**
         * Called with all addresses that could not be resolved to valid recipients.
         */
        public void matchesNotFound(Set<String> unfoundAddresses);
    }

    public RecipientAlternatesAdapter(Context context, BaseRecipientAdapter adapter, String displayName,
            OnCheckedItemChangedListener listener) {
        mDisplayName = displayName;
        mAdapter = adapter;
        mInflater = LayoutInflater.from(context);
        mCheckedItemChangedListener = listener;

        doQuery(displayName);
    }

    private void doQuery(String displayName) {
        final List<RecipientEntry> newEntries = mAdapter.getAlternativeRecipients(displayName);
        updateEntries(newEntries);
    }

    @Override
    public int getCount() {
        return mEntries.size();
    }

    @Override
    public RecipientEntry getItem(int position) {
        return mEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getContactId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final RecipientEntry entry = getEntries().get(position);
        String displayName = entry.getDisplayName();

        if (displayName.equals(mDisplayName)) {
            mCheckedItemPosition = position;
            if (mCheckedItemChangedListener != null) {
                mCheckedItemChangedListener.onCheckedItemChanged(mCheckedItemPosition);
            }
        }

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
        final ImageView imageView = (ImageView) itemView.findViewById(getPhotoId());
        displayNameView.setText(displayName);
        if (!TextUtils.isEmpty(destination)) {
            destinationView.setText(destination);
        } else {
            destinationView.setText(null);
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

    /**
     * Get a HashMap of address to RecipientEntry that contains all contact
     * information for a contact with the provided display name, if one exists. This
     * may block the UI, so run it in an async task.
     *
     * @param inDisplayNames Array of display names on which to perform the lookup.
     * @param callback RecipientMatchCallback called when a match or matches are found.
     */
    public static void getMatchingRecipients(BaseRecipientAdapter adapter, ArrayList<String> inDisplayNames,
            RecipientMatchCallback callback) {
        int addressesSize = Math.min(MAX_LOOKUPS, inDisplayNames.size());
        HashSet<String> displayNames = new HashSet<>();
        for (int i = 0; i < addressesSize; i++) {
            displayNames.add(inDisplayNames.get(i));
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Doing reverse lookup for " + inDisplayNames.toString());
        }

        final HashMap<String, List<RecipientEntry>> recipientEntryLists = adapter.getAlternativeRecipients(displayNames);
        final HashMap<String, RecipientEntry> recipientEntries = new HashMap<>();
        for (String displayName : recipientEntryLists.keySet()) {
            final List<RecipientEntry> entryList = recipientEntryLists.get(displayName);
            for (RecipientEntry entry : entryList) {
                /*
                 * In certain situations, we may have two results for one display name, where one of the
                 * results is just the email address, and the other has a name and photo, so we want
                 * to use the better one.
                 */
                final RecipientEntry recipientEntry =
                        getBetterRecipient(recipientEntries.get(displayName), entry);

                recipientEntries.put(displayName, recipientEntry);

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Received reverse look up information for " + displayName
                            + " RESULTS: "
                            + " NAME : " + recipientEntry.getDisplayName()
                            + " CONTACT ID : " + recipientEntry.getContactId()
                            + " ADDRESS :" + recipientEntry.getDestination());
                }
            }
        }
        callback.matchesFound(recipientEntries);

        // See if any entries did not resolve;
        final Set<String> matchesNotFound = new HashSet<>();
        if (recipientEntries.size() < displayNames.size()) {
            HashSet<String> unresolvedAddresses = new HashSet<>();
            for (String address : displayNames) {
                if (!recipientEntries.containsKey(address)) {
                    unresolvedAddresses.add(address);
                }
            }

            matchesNotFound.addAll(unresolvedAddresses);
        }
        callback.matchesNotFound(matchesNotFound);
    }

    private List<RecipientEntry> getEntries() {
        return mEntries;
    }

    public RecipientEntry getRecipientEntry(int position) {
        return (RecipientEntry) getItem(position);
    }

    /** Resets {@link #mEntries} and notify the event to its parent ListView. */
    private void updateEntries(List<RecipientEntry> newEntries) {
        mEntries = newEntries;
        notifyDataSetChanged();
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
     * Returns an id for ImageView in an item View for showing photo image for a person. In default
     * {@link android.R.id#icon} is returned.
     */
    protected int getPhotoId() {
        return android.R.id.icon;
    }

    /**
     * Given two {@link RecipientEntry}s for the same email address, this will return the one that
     * contains more complete information for display purposes. Defaults to <code>entry2</code> if
     * no significant differences are found.
     */
    public static RecipientEntry getBetterRecipient(final RecipientEntry entry1,
            final RecipientEntry entry2) {
        // If only one has passed in, use it
        if (entry2 == null) {
            return entry1;
        }

        if (entry1 == null) {
            return entry2;
        }

        // If only one has a display name, use it
        if (!TextUtils.isEmpty(entry1.getDisplayName())
                && TextUtils.isEmpty(entry2.getDisplayName())) {
            return entry1;
        }

        if (!TextUtils.isEmpty(entry2.getDisplayName())
                && TextUtils.isEmpty(entry1.getDisplayName())) {
            return entry2;
        }

        // If only one has a display name that is not the same as the destination, use it
        if (!TextUtils.equals(entry1.getDisplayName(), entry1.getDestination())
                && TextUtils.equals(entry2.getDisplayName(), entry2.getDestination())) {
            return entry1;
        }

        if (!TextUtils.equals(entry2.getDisplayName(), entry2.getDestination())
                && TextUtils.equals(entry1.getDisplayName(), entry1.getDestination())) {
            return entry2;
        }

        // If only one has a photo, use it
        if (entry1.getPhotoBytes() != null && entry2.getPhotoBytes() == null) {
            return entry1;
        }

        if (entry2.getPhotoBytes() != null && entry1.getPhotoBytes() == null) {
            return entry2;
        }

        // Go with the second option as a default
        return entry2;
    }

    public static interface OnCheckedItemChangedListener {
        public void onCheckedItemChanged(int position);
    }
}
