package sk.rajniak.chips;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.QwertyKeyListener;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.ListAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import sk.rajniak.chips.model.RecipientEntry;
import sk.rajniak.chips.recipientchip.DrawableRecipientChip;
import sk.rajniak.chips.recipientchip.VisibleRecipientChip;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView {

    private static final String TAG = RecipientEditTextView.class.getSimpleName();

    private static final char COMMIT_CHAR_COMMA = ',';

    private static final char COMMIT_CHAR_SEMICOLON = ';';

    private final RecipientTextWatcher mTextWatcher;

    private Tokenizer mTokenizer;

    private Validator mValidator;

    private static int sSelectedTextColor = -1;

    // Resources for displaying chips.

    private Drawable mChipBackground = null;

    private Drawable mChipBackgroundPressed;

    private Drawable mChipDelete;

    private int mChipPadding;

    private int mAlternatesLayout;

    private Bitmap mDefaultContactPhoto;

    private TextView mMoreItem;

    private float mChipHeight;

    private float mChipFontSize;

    private Drawable mInvalidChipBackground;

    private float mLineSpacingExtra;

    private int mMaxLines;

    private int mActionBarHeight;

    /** Flag that is set to true, when we are creating new chip and we do not want another to interfere */
    private boolean mNoChips = false;

    /** Chips count describing number of chips waiting to be chip-ify from the content */
    private int mPendingChipsCount = 0;

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setChipDimensions(context, attrs);
        if (sSelectedTextColor == -1) {
            sSelectedTextColor = context.getResources().getColor(android.R.color.white);
        }
        mTextWatcher = new RecipientTextWatcher();
        addTextChangedListener(mTextWatcher);
    }

    private void setChipDimensions(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecipientEditTextView, 0,
                0);
        Resources r = getContext().getResources();

        mChipBackground = a.getDrawable(R.styleable.RecipientEditTextView_chipBackground);
        if (mChipBackground == null) {
            mChipBackground = r.getDrawable(R.drawable.chip_background);
        }
        mChipBackgroundPressed = a
                .getDrawable(R.styleable.RecipientEditTextView_chipBackgroundPressed);
        if (mChipBackgroundPressed == null) {
            mChipBackgroundPressed = r.getDrawable(R.drawable.chip_background_selected);
        }
        mChipDelete = a.getDrawable(R.styleable.RecipientEditTextView_chipDelete);
        if (mChipDelete == null) {
            mChipDelete = r.getDrawable(R.drawable.chip_delete);
        }
        mChipPadding = a.getDimensionPixelSize(R.styleable.RecipientEditTextView_chipPadding, -1);
        if (mChipPadding == -1) {
            mChipPadding = (int) r.getDimension(R.dimen.chip_padding);
        }
        mAlternatesLayout = a.getResourceId(R.styleable.RecipientEditTextView_chipAlternatesLayout,
                -1);
        if (mAlternatesLayout == -1) {
            mAlternatesLayout = R.layout.chips_alternate_item;
        }

        mDefaultContactPhoto = BitmapFactory.decodeResource(r, R.drawable.ic_contact_picture);

        mMoreItem = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.more_item, null);

        mChipHeight = a.getDimensionPixelSize(R.styleable.RecipientEditTextView_chipHeight, -1);
        if (mChipHeight == -1) {
            mChipHeight = r.getDimension(R.dimen.chip_height);
        }
        mChipFontSize = a.getDimensionPixelSize(R.styleable.RecipientEditTextView_chipFontSize, -1);
        if (mChipFontSize == -1) {
            mChipFontSize = r.getDimension(R.dimen.chip_text_size);
        }
        mInvalidChipBackground = a
                .getDrawable(R.styleable.RecipientEditTextView_invalidChipBackground);
        if (mInvalidChipBackground == null) {
            mInvalidChipBackground = r.getDrawable(R.drawable.chip_background_invalid);
        }
        mLineSpacingExtra =  r.getDimension(R.dimen.line_spacing_extra);
        mMaxLines = r.getInteger(R.integer.chips_max_lines);
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            mActionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources()
                    .getDisplayMetrics());
        }
        a.recycle();
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer) {
        mTokenizer = tokenizer;
        super.setTokenizer(mTokenizer);
    }

    @Override
    public void setValidator(Validator validator) {
        mValidator = validator;
        super.setValidator(validator);
    }

    private class RecipientTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Do nothing.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            int length = s.length();
            // Make sure there is content there to parse and that it is
            // not just the commit character.
            if (length > 1) {
                if (lastCharacterIsCommitCharacter(s)) {
                    commitByCharacter();
                    return;
                }
            }
        }

        private boolean lastCharacterIsCommitCharacter(CharSequence s) {
            char last;
            int end = getSelectionEnd() == 0 ? 0 : getSelectionEnd() - 1;
            int len = length() - 1;
            if (end != len) {
                last = s.charAt(end);
            } else {
                last = s.charAt(len);
            }
            return last == COMMIT_CHAR_COMMA || last == COMMIT_CHAR_SEMICOLON;
        }
    }

    private void commitByCharacter() {
        // We can't possibly commit by character if we can't tokenize.
        if (mTokenizer == null) {
            return;
        }
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(editable, end);
        if (shouldCreateChip(start, end)) {
            commitChip(start, end, editable);
        }
        setSelection(getText().length());
    }

    private boolean shouldCreateChip(int start, int end) {
        return !mNoChips && hasFocus() && enoughToFilter() && !alreadyHasChip(start, end);
    }

    private boolean alreadyHasChip(int start, int end) {
        if (mNoChips) {
            return true;
        }
        DrawableRecipientChip[] chips =
                getSpannable().getSpans(start, end, DrawableRecipientChip.class);
        if ((chips == null || chips.length == 0)) {
            return false;
        }
        return true;
    }

    private Spannable getSpannable() {
        return getText();
    }

    private boolean commitChip(int start, int end, Editable editable) {
        ListAdapter adapter = getAdapter();
        if (adapter != null && adapter.getCount() > 0 && enoughToFilter()
                && end == getSelectionEnd()) {
            // choose the first entry.
            // TODO: when we allow drop down items
            // submitItemAtPosition(0);
            dismissDropDown();
            return true;
        } else {
            int tokenEnd = mTokenizer.findTokenEnd(editable, start);
            if (editable.length() > tokenEnd + 1) {
                char charAt = editable.charAt(tokenEnd + 1);
                if (charAt == COMMIT_CHAR_COMMA || charAt == COMMIT_CHAR_SEMICOLON) {
                    tokenEnd++;
                }
            }
            String text = editable.toString().substring(start, tokenEnd).trim();
            clearComposingText();
            if (text != null && text.length() > 0 && !text.equals(" ")) {
                RecipientEntry entry = createTokenizedEntry(text);
                if (entry != null) {
                    QwertyKeyListener.markAsReplaced(editable, start, end, "");
                    CharSequence chipText = createChip(entry, false);
                    if (chipText != null && start > -1 && end > -1) {
                        editable.replace(start, end, chipText);
                    }
                }
                // Only dismiss the dropdown if it is related to the text we
                // just committed.
                // For paste, it may not be as there are possibly multiple
                // tokens being added.
                if (end == getSelectionEnd()) {
                    dismissDropDown();
                }
                sanitizeBetween();
                return true;
            }
        }
        return false;
    }

    private RecipientEntry createTokenizedEntry(final String token) {
        if (TextUtils.isEmpty(token)) {
            return null;
        }

        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(token);
        String display;
        boolean isValid = isValid(token);
        if (isValid && tokens.length > 0) {
            // If we can get a name from tokenizing, then generate an entry from
            // this.
            display = tokens[0].getName();
            if (!TextUtils.isEmpty(display)) {
                return RecipientEntry.constructGeneratedEntry(display, tokens[0].getAddress(), true);
            } else {
                display = tokens[0].getAddress();
                if (!TextUtils.isEmpty(display)) {
                    return RecipientEntry.constructFakeEntry(display, true);
                }
            }
        }
        // Unable to validate the token or to create a valid token from it.
        // Just create a chip the user can edit.
        String validatedToken = null;
        if (mValidator != null && !isValid) {
            // Try fixing up the entry using the validator.
            validatedToken = mValidator.fixText(token).toString();
            if (!TextUtils.isEmpty(validatedToken)) {
                if (validatedToken.contains(token)) {
                    // protect against the case of a validator with a null
                    // domain,
                    // which doesn't add a domain to the token
                    Rfc822Token[] tokenized = Rfc822Tokenizer.tokenize(validatedToken);
                    if (tokenized.length > 0) {
                        validatedToken = tokenized[0].getAddress();
                        isValid = true;
                    }
                } else {
                    // We ran into a case where the token was invalid and
                    // removed
                    // by the validator. In this case, just use the original
                    // token
                    // and let the user sort out the error chip.
                    validatedToken = null;
                    isValid = false;
                }
            }
        }
        // Otherwise, fallback to just creating an editable email address chip.
        return RecipientEntry.constructFakeEntry(
                !TextUtils.isEmpty(validatedToken) ? validatedToken : token, isValid);
    }

    private boolean isValid(String text) {
        return mValidator == null || mValidator.isValid(text);
    }

    private CharSequence createChip(RecipientEntry entry, boolean pressed) {
        String displayText = createAddressText(entry);
        if (TextUtils.isEmpty(displayText)) {
            return null;
        }
        SpannableString chipText;
        // Always leave a blank space at the end of a chip.
        int textLength = displayText.length() - 1;
        chipText = new SpannableString(displayText);
        if (!mNoChips) {
            try {
                DrawableRecipientChip chip = constructChipSpan(entry, pressed);
                chipText.setSpan(chip, 0, textLength,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                chip.setOriginalText(chipText.toString());
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
        }
        return chipText;
    }

    /** Use this method to generate text to add to the list of addresses. */
    private String createAddressText(RecipientEntry entry) {
        String display = entry.getDisplayName();
        String address = entry.getDestination();
        if (TextUtils.isEmpty(display) || TextUtils.equals(display, address)) {
            display = null;
        }

        if (address != null) {
            // Tokenize out the address in case the address already
            // contained the username as well.
            Rfc822Token[] tokenized = Rfc822Tokenizer.tokenize(address);
            if (tokenized.length > 0) {
                address = tokenized[0].getAddress();
            }
        }
        Rfc822Token token = new Rfc822Token(display, address, null);
        String trimmedDisplayText = token.toString().trim();
        int index = trimmedDisplayText.indexOf(",");

        return mTokenizer != null && !TextUtils.isEmpty(trimmedDisplayText)
                && index < trimmedDisplayText.length() - 1 ? (String) mTokenizer
                .terminateToken(trimmedDisplayText) : trimmedDisplayText;
    }

    private DrawableRecipientChip constructChipSpan(RecipientEntry contact, boolean pressed)
            throws NullPointerException {
        if (mChipBackground == null) {
            throw new NullPointerException(
                    "Unable to render any chips as setChipDimensions was not called.");
        }

        TextPaint paint = getPaint();
        float defaultSize = paint.getTextSize();
        int defaultColor = paint.getColor();

        Bitmap tmpBitmap;
        if (pressed) {
            tmpBitmap = createSelectedChip(contact, paint);
        } else {
            tmpBitmap = createUnselectedChip(contact, paint);
        }

        // Pass the full text, un-ellipsized, to the chip.
        Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
        result.setBounds(0, 0, tmpBitmap.getWidth(), tmpBitmap.getHeight());
        DrawableRecipientChip recipientChip = new VisibleRecipientChip(result, contact);
        // Return text to the original size.
        paint.setTextSize(defaultSize);
        paint.setColor(defaultColor);
        return recipientChip;
    }

    private Bitmap createSelectedChip(RecipientEntry contact, TextPaint paint) {
        // Ellipsize the text so that it takes AT MOST the entire width of the
        // auto-complete text entry area. Make sure to leave space for padding
        // on the sides.
        int height = (int) mChipHeight;
        int deleteWidth = height;
        float[] widths = new float[1];
        paint.getTextWidths(" ", widths);
        CharSequence ellipsizedText = ellipsizeText(createChipDisplayText(contact), paint,
                calculateAvailableWidth() - deleteWidth - widths[0]);

        // Make sure there is a minimum chip width so the user can ALWAYS
        // tap a chip without difficulty.
        int width = Math.max(deleteWidth * 2, (int) Math.floor(paint.measureText(ellipsizedText, 0,
                ellipsizedText.length()))
                + (mChipPadding * 2) + deleteWidth);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        if (mChipBackgroundPressed != null) {
            mChipBackgroundPressed.setBounds(0, 0, width, height);
            mChipBackgroundPressed.draw(canvas);
            paint.setColor(sSelectedTextColor);
            // Vertically center the text in the chip.
            canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding,
                    getTextYOffset((String) ellipsizedText, paint, height), paint);
            // Make the delete a square.
            Rect backgroundPadding = new Rect();
            mChipBackgroundPressed.getPadding(backgroundPadding);
            mChipDelete.setBounds(width - deleteWidth + backgroundPadding.left,
                    0 + backgroundPadding.top,
                    width - backgroundPadding.right,
                    height - backgroundPadding.bottom);
            mChipDelete.draw(canvas);
        } else {
            Log.w(TAG, "Unable to draw a background for the chips as it was never set");
        }
        return tmpBitmap;
    }

    private CharSequence ellipsizeText(CharSequence text, TextPaint paint, float maxWidth) {
        paint.setTextSize(mChipFontSize);
        if (maxWidth <= 0 && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Max width is negative: " + maxWidth);
        }
        return TextUtils.ellipsize(text, paint, maxWidth,
                TextUtils.TruncateAt.END);
    }

    /** Use this method to generate text to display in a chip. */
    private String createChipDisplayText(RecipientEntry entry) {
        String display = entry.getDisplayName();
        String address = entry.getDestination();
        if (TextUtils.isEmpty(display) || TextUtils.equals(display, address)) {
            display = null;
        }
        if (!TextUtils.isEmpty(display)) {
            return display;
        } else if (!TextUtils.isEmpty(address)){
            return address;
        } else {
            return new Rfc822Token(display, address, null).toString();
        }
    }

    /**
     * Get the max amount of space a chip can take up. The formula takes into
     * account the width of the EditTextView, any view padding, and padding
     * that will be added to the chip.
     */
    private float calculateAvailableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight() - (mChipPadding * 2);
    }

    private static float getTextYOffset(String text, TextPaint paint, int height) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int textHeight = bounds.bottom - bounds.top ;
        return height - ((height - textHeight) / 2) - (int)paint.descent();
    }

    private Bitmap createUnselectedChip(RecipientEntry contact, TextPaint paint) {
        // Ellipsize the text so that it takes AT MOST the entire width of the
        // auto-complete text entry area. Make sure to leave space for padding
        // on the sides.
        int height = (int) mChipHeight;
        int iconWidth = height;
        float[] widths = new float[1];
        paint.getTextWidths(" ", widths);
        CharSequence ellipsizedText = ellipsizeText(createChipDisplayText(contact), paint,
                calculateAvailableWidth() - iconWidth - widths[0]);
        // Make sure there is a minimum chip width so the user can ALWAYS
        // tap a chip without difficulty.
        int width = Math.max(iconWidth * 2, (int) Math.floor(paint.measureText(ellipsizedText, 0,
                ellipsizedText.length()))
                + (mChipPadding * 2) + iconWidth);

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        Drawable background = getChipBackground(contact);
        if (background != null) {
            background.setBounds(0, 0, width, height);
            background.draw(canvas);

            // Don't draw photos for recipients that have been typed in OR generated on the fly.
            long contactId = contact.getContactId();
            boolean drawPhotos = (contactId != RecipientEntry.INVALID_CONTACT
                            && (contactId != RecipientEntry.GENERATED_CONTACT &&
                            !TextUtils.isEmpty(contact.getDisplayName())));
            if (drawPhotos) {
                byte[] photoBytes = contact.getPhotoBytes();
                // There may not be a photo yet if anything but the first contact address
                // was selected.
                if (photoBytes == null && contact.getContactId() > 0) {
                    // TODO: cache this in the recipient entry?
                    // TODO: implement fetching of photo
                    // getAdapter().fetchPhoto(contact, contact.getContactId());
                    photoBytes = contact.getPhotoBytes();
                }

                Bitmap photo;
                if (photoBytes != null) {
                    photo = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.length);
                } else {
                    // TODO: can the scaled down default photo be cached?
                    photo = mDefaultContactPhoto;
                }
                // Draw the photo on the left side.
                if (photo != null) {
                    RectF src = new RectF(0, 0, photo.getWidth(), photo.getHeight());
                    Rect backgroundPadding = new Rect();
                    mChipBackground.getPadding(backgroundPadding);
                    RectF dst = new RectF(width - iconWidth + backgroundPadding.left,
                            0 + backgroundPadding.top,
                            width - backgroundPadding.right,
                            height - backgroundPadding.bottom);
                    Matrix matrix = new Matrix();
                    matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
                    canvas.drawBitmap(photo, matrix, paint);
                }
            }

            paint.setColor(getContext().getResources().getColor(android.R.color.black));
            // Vertically center the text in the chip.
            canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding,
                    getTextYOffset((String)ellipsizedText, paint, height), paint);
        } else {
            Log.w(TAG, "Unable to draw a background for the chips as it was never set");
        }
        return tmpBitmap;
    }

    /**
     * Get the background drawable for a RecipientChip.
     */
    private Drawable getChipBackground(RecipientEntry contact) {
        return contact.isValid() ? mChipBackground : mInvalidChipBackground;
    }

    private void sanitizeBetween() {
        // Don't sanitize while we are waiting for content to chip-ify.
        if (mPendingChipsCount > 0) {
            return;
        }
        // Find the last chip.
        DrawableRecipientChip[] recipients = getSortedRecipients();
        if (recipients != null && recipients.length > 0) {
            DrawableRecipientChip last = recipients[recipients.length - 1];
            DrawableRecipientChip beforeLast = null;
            if (recipients.length > 1) {
                beforeLast = recipients[recipients.length - 2];
            }
            int startLooking = 0;
            int end = getSpannable().getSpanStart(last);
            if (beforeLast != null) {
                startLooking = getSpannable().getSpanEnd(beforeLast);
                Editable text = getText();
                if (startLooking == -1 || startLooking > text.length() - 1) {
                    // There is nothing after this chip.
                    return;
                }
                if (text.charAt(startLooking) == ' ') {
                    startLooking++;
                }
            }
            if (startLooking >= 0 && end >= 0 && startLooking < end) {
                getText().delete(startLooking, end);
            }
        }
    }

    private DrawableRecipientChip[] getSortedRecipients() {
        DrawableRecipientChip[] recipients = getSpannable()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);
        ArrayList<DrawableRecipientChip> recipientsList = new ArrayList<>(
                Arrays.asList(recipients));
        final Spannable spannable = getSpannable();
        Collections.sort(recipientsList, new Comparator<DrawableRecipientChip>() {

            @Override
            public int compare(DrawableRecipientChip first, DrawableRecipientChip second) {
                int firstStart = spannable.getSpanStart(first);
                int secondStart = spannable.getSpanStart(second);
                if (firstStart < secondStart) {
                    return -1;
                } else if (firstStart > secondStart) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return recipientsList.toArray(new DrawableRecipientChip[recipientsList.size()]);
    }
}
