package sk.rajniak.chips.recipientchip;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;

import sk.rajniak.chips.model.RecipientEntry;

/**
 * VisibleRecipientChip defines an ImageSpan that contains information relevant to a
 * particular recipient and renders a background asset to go with it.
 */
public class VisibleRecipientChip extends ImageSpan implements DrawableRecipientChip {
    private final SimpleRecipientChip mDelegate;

    public VisibleRecipientChip(final Drawable drawable, final RecipientEntry entry) {
        super(drawable, DynamicDrawableSpan.ALIGN_BOTTOM);

        mDelegate = new SimpleRecipientChip(entry);
    }

    @Override
    public void setSelected(final boolean selected) {
        mDelegate.setSelected(selected);
    }

    @Override
    public boolean isSelected() {
        return mDelegate.isSelected();
    }

    @Override
    public CharSequence getDisplay() {
        return mDelegate.getDisplay();
    }

    @Override
    public CharSequence getValue() {
        return mDelegate.getValue();
    }

    @Override
    public long getContactId() {
        return mDelegate.getContactId();
    }

    @Override
    public RecipientEntry getEntry() {
        return mDelegate.getEntry();
    }

    @Override
    public void setOriginalText(final String text) {
        mDelegate.setOriginalText(text);
    }

    @Override
    public CharSequence getOriginalText() {
        return mDelegate.getOriginalText();
    }

    @Override
    public Rect getBounds() {
        return getDrawable().getBounds();
    }

    @Override
    public void draw(final Canvas canvas) {
        getDrawable().draw(canvas);
    }

    @Override
    public String toString() {
        return mDelegate.toString();
    }
}
