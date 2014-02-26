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
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sk.rajniak.chips.R;

public abstract class BaseRecipientAdapter extends BaseAdapter implements Filterable {
    private static final String TAG = BaseRecipientAdapter.class.getSimpleName();

    private static final boolean DEBUG = false;

    public static final int MAX_LOOKUPS = 50;

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

    public interface EntriesUpdatedObserver {
        public void onChanged(List<RecipientEntry> entries);
    }

    public void registerUpdateObserver(EntriesUpdatedObserver observer) {
        mEntriesUpdatedObserver = observer;
    }

    public interface RecipientMatchCallback {
        public void matchesFound(Map<String, RecipientEntry> results);
        /**
         * Called with all addresses that could not be resolved to valid recipients.
         */
        public void matchesNotFound(Set<String> unfoundAddresses);
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

    /**
     * Get a HashMap of address to RecipientEntry that contains all contact
     * information for a contact with the provided address, if one exists. This
     * may block the UI, so run it in an async task.
     *
     * @param inAddresses Array of addresses on which to perform the lookup.
     * @param callback RecipientMatchCallback called when a match or matches are found.
     */
    public static void getMatchingRecipients(BaseRecipientAdapter adapter, ArrayList<String> inAddresses,
            RecipientMatchCallback callback) {
        int addressesSize = Math.min(MAX_LOOKUPS, inAddresses.size());
        HashSet<String> addresses = new HashSet<>();
        for (int i = 0; i < addressesSize; i++) {
            Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(inAddresses.get(i).toLowerCase());
            addresses.add(tokens.length > 0 ? tokens[0].getAddress() : inAddresses.get(i));
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Doing reverse lookup for " + addresses.toString());
        }

        HashMap<String, RecipientEntry> recipientEntries = adapter.getMatchingRecipients(addresses);
        callback.matchesFound(recipientEntries);

        // See if any entries did not resolve;
        final Set<String> matchesNotFound = new HashSet<>();
        if (recipientEntries.size() < addresses.size()) {
            HashSet<String> unresolvedAddresses = new HashSet<>();
            for (String address : addresses) {
                if (!recipientEntries.containsKey(address)) {
                    unresolvedAddresses.add(address);
                }
            }

            matchesNotFound.addAll(unresolvedAddresses);
        }
        callback.matchesNotFound(matchesNotFound);
    }

    protected abstract HashMap<String, RecipientEntry> getMatchingRecipients(HashSet<String> inAddresses);

    protected abstract HashMap<String, List<RecipientEntry>> getMatchingRecipients(CharSequence constraint);

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
