package sk.rajniak.chips.model;

import android.net.Uri;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;

public class RecipientEntry {

    public static final int INVALID_CONTACT = -1;
    /**
     * A GENERATED_CONTACT is one that was created based entirely on
     * information passed in to the RecipientEntry from an external source
     * that is not a real contact.
     */
    public static final int GENERATED_CONTACT = -2;

    public static final int ENTRY_TYPE_PERSON = 0;

    public static final int ENTRY_TYPE_SIZE = 1;

    private final int mEntryType;

    private final boolean mIsFirstLevel;

    private final boolean mIsValid;

    private String mDisplayName;

    private String mDestination;

    private long mContactId;

    /**
     * This can be updated after this object being constructed, when the photo is fetched
     * from remote directories.
     */
    private byte[] mPhotoBytes;

    private RecipientEntry(int entryType, String displayName, String destination, long contactId, boolean isFirstLevel,
            boolean isValid) {
        mEntryType = entryType;
        mIsFirstLevel = isFirstLevel;
        mDisplayName = displayName;
        mDestination = destination;
        mContactId = contactId;
        mPhotoBytes = null;
        mIsValid = isValid;
    }

    /**
     * Construct a RecipientEntry from just an address that has been entered.
     * This address has not been resolved to a contact and therefore does not
     * have a contact id or photo.
     */
    public static RecipientEntry constructFakeEntry(final String address, final boolean isValid) {
        final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(address);
        final String tokenizedAddress = tokens.length > 0 ? tokens[0].getAddress() : address;

        return new RecipientEntry(ENTRY_TYPE_PERSON, tokenizedAddress, tokenizedAddress, INVALID_CONTACT, true, isValid);
    }

    /**
     * Construct a RecipientEntry from just an address that has been entered
     * with both an associated display name. This address has not been resolved
     * to a contact and therefore does not have a contact id or photo.
     */
    public static RecipientEntry constructGeneratedEntry(String display, String address,
            boolean isValid) {
        return new RecipientEntry(ENTRY_TYPE_PERSON, display, address, GENERATED_CONTACT, true, isValid);
    }

    public static RecipientEntry constructTopLevelEntry(String displayName, String destination, long contactId,
            boolean isValid) {
        return new RecipientEntry(ENTRY_TYPE_PERSON, displayName, destination, contactId, true, isValid);
    }

    /**
     * Determine if this was a RecipientEntry created from recipient info or
     * an entry from contacts.
     */
    public static boolean isCreatedRecipient(long id) {
        return id == RecipientEntry.INVALID_CONTACT || id == RecipientEntry.GENERATED_CONTACT;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDestination() {
        return mDestination;
    }

    public long getContactId() {
        return mContactId;
    }

    public boolean isValid() {
        return mIsValid;
    }

    public byte[] getPhotoBytes() {
        return mPhotoBytes;
    }

    public int getEntryType() {
        return mEntryType;
    }

    public boolean isSelectable() {
        return mEntryType == ENTRY_TYPE_PERSON;
    }

    public boolean isFirstLevel() {
        return mIsFirstLevel;
    }
}
