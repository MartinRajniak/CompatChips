package sk.rajniak.chips.recipientchip;

import android.text.TextUtils;

import sk.rajniak.chips.model.RecipientEntry;

class SimpleRecipientChip implements BaseRecipientChip {
    private final CharSequence mDisplay;

    private final CharSequence mValue;

    private final long mContactId;

    private final RecipientEntry mEntry;

    private boolean mSelected = false;

    private CharSequence mOriginalText;

    public SimpleRecipientChip(final RecipientEntry entry) {
        mDisplay = entry.getDisplayName();
        mValue = entry.getDestination().trim();
        mContactId = entry.getContactId();
        mEntry = entry;
    }

    @Override
    public void setSelected(final boolean selected) {
        mSelected = selected;
    }

    @Override
    public boolean isSelected() {
        return mSelected;
    }

    @Override
    public CharSequence getDisplay() {
        return mDisplay;
    }

    @Override
    public CharSequence getValue() {
        return mValue;
    }

    @Override
    public long getContactId() {
        return mContactId;
    }

    @Override
    public RecipientEntry getEntry() {
        return mEntry;
    }

    @Override
    public void setOriginalText(final String text) {
        if (TextUtils.isEmpty(text)) {
            mOriginalText = text;
        } else {
            mOriginalText = text.trim();
        }
    }

    @Override
    public CharSequence getOriginalText() {
        return !TextUtils.isEmpty(mOriginalText) ? mOriginalText : mEntry.getDestination();
    }

    @Override
    public String toString() {
        return mDisplay + " <" + mValue + ">";
    }
}