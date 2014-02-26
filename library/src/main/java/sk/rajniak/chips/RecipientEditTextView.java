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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.MultiAutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sk.rajniak.chips.model.BaseRecipientAdapter;
import sk.rajniak.chips.model.RecipientEntry;
import sk.rajniak.chips.recipientchip.DrawableRecipientChip;
import sk.rajniak.chips.recipientchip.InvisibleRecipientChip;
import sk.rajniak.chips.recipientchip.VisibleRecipientChip;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView implements
        AdapterView.OnItemClickListener {

    private static final String TAG = RecipientEditTextView.class.getSimpleName();

    private static final char COMMIT_CHAR_COMMA = ',';

    private static final char COMMIT_CHAR_SEMICOLON = ';';

    private static final char COMMIT_CHAR_SPACE = ' ';

    private static int DISMISS = "dismiss".hashCode();

    // TODO: get correct number/ algorithm from with UX.
    private static final int CHIP_LIMIT = 2;

    private static final int MAX_CHIPS_PARSED = 50;

    private RecipientTextWatcher mTextWatcher;

    private Tokenizer mTokenizer;

    private Validator mValidator;

    private Handler mHandler;

    // Obtain the enclosing scroll view, if it exists, so that the view can be
    // scrolled to show the last line of chips content.
    private ScrollView mScrollView;

    private ListPopupWindow mAlternatesPopup;

    private static int sExcessTopPadding = -1;

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

    private final ArrayList<String> mPendingChips = new ArrayList<>();

    private ArrayList<DrawableRecipientChip> mTemporaryRecipients;

    private IndividualReplacementTask mIndividualReplacements;

    private ImageSpan mMoreChip;

    private ArrayList<DrawableRecipientChip> mRemovedSpans;

    private DrawableRecipientChip mSelectedChip;

    private boolean mShouldShrink = true;

    private boolean mTriedGettingScrollView;

    private final Runnable mAddTextWatcher = new Runnable() {
        @Override
        public void run() {
            if (mTextWatcher == null) {
                mTextWatcher = new RecipientTextWatcher();
                addTextChangedListener(mTextWatcher);
            }
        }
    };

    private Runnable mHandlePendingChips = new Runnable() {

        @Override
        public void run() {
            handlePendingChips();
        }

    };

    private Runnable mDelayedShrink = new Runnable() {

        @Override
        public void run() {
            shrink();
        }

    };

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setChipDimensions(context, attrs);
        if (sSelectedTextColor == -1) {
            sSelectedTextColor = context.getResources().getColor(android.R.color.white);
        }
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setOnItemClickListener(this);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == DISMISS) {
                    // TODO: use compat ListPopupWindow
                    ((ListPopupWindow) msg.obj).dismiss();
                    return;
                }
                super.handleMessage(msg);
            }
        };
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
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        super.onFocusChanged(hasFocus, direction, previous);
        if (!hasFocus) {
            shrink();
        } else {
            expand();
        }
    }

    private void shrink() {
        if (mTokenizer == null) {
            return;
        }
        long contactId = mSelectedChip != null ? mSelectedChip.getEntry().getContactId() : -1;
        if (mSelectedChip != null && contactId != RecipientEntry.INVALID_CONTACT
                && (contactId != RecipientEntry.GENERATED_CONTACT)) {
            clearSelectedChip();
        } else {
            if (getWidth() <= 0) {
                // We don't have the width yet which means the view hasn't been drawn yet
                // and there is no reason to attempt to commit chips yet.
                // This focus lost must be the result of an orientation change
                // or an initial rendering.
                // Re-post the shrink for later.
                mHandler.removeCallbacks(mDelayedShrink);
                mHandler.post(mDelayedShrink);
                return;
            }
            // Reset any pending chips as they would have been handled
            // when the field lost focus.
            if (mPendingChipsCount > 0) {
                postHandlePendingChips();
            } else {
                Editable editable = getText();
                int end = getSelectionEnd();
                int start = mTokenizer.findTokenStart(editable, end);
                DrawableRecipientChip[] chips =
                        getSpannable().getSpans(start, end, DrawableRecipientChip.class);
                if ((chips == null || chips.length == 0)) {
                    Editable text = getText();
                    int whatEnd = mTokenizer.findTokenEnd(text, start);
                    // This token was already tokenized, so skip past the ending token.
                    if (whatEnd < text.length() && text.charAt(whatEnd) == ',') {
                        whatEnd = movePastTerminators(whatEnd);
                    }
                    // In the middle of chip; treat this as an edit
                    // and commit the whole token.
                    int selEnd = getSelectionEnd();
                    if (whatEnd != selEnd) {
                        handleEdit(start, whatEnd);
                    } else {
                        commitChip(start, end, editable);
                    }
                }
            }
            mHandler.post(mAddTextWatcher);
        }
        createMoreChip();
    }

    private void expand() {
        if (mShouldShrink) {
            setMaxLines(Integer.MAX_VALUE);
        }
        removeMoreChip();
        setCursorVisible(true);
        Editable text = getText();
        setSelection(text != null && text.length() > 0 ? text.length() : 0);
        // If there are any temporary chips, try replacing them now that the user
        // has expanded the field.
        if (mTemporaryRecipients != null && mTemporaryRecipients.size() > 0) {
            new RecipientReplacementTask().execute();
            mTemporaryRecipients = null;
        }
    }

    /**
     * Replace the more chip, if it exists, with all of the recipient chips it had
     * replaced when the RecipientEditTextView gains focus.
     */
    private void removeMoreChip() {
        if (mMoreChip != null) {
            Spannable span = getSpannable();
            span.removeSpan(mMoreChip);
            mMoreChip = null;
            // Re-add the spans that were removed.
            if (mRemovedSpans != null && mRemovedSpans.size() > 0) {
                // Recreate each removed span.
                DrawableRecipientChip[] recipients = getSortedRecipients();
                // Start the search for tokens after the last currently visible
                // chip.
                if (recipients == null || recipients.length == 0) {
                    return;
                }
                int end = span.getSpanEnd(recipients[recipients.length - 1]);
                Editable editable = getText();
                for (DrawableRecipientChip chip : mRemovedSpans) {
                    int chipStart;
                    int chipEnd;
                    String token;
                    // Need to find the location of the chip, again.
                    token = (String) chip.getOriginalText();
                    // As we find the matching recipient for the remove spans,
                    // reduce the size of the string we need to search.
                    // That way, if there are duplicates, we always find the correct
                    // recipient.
                    chipStart = editable.toString().indexOf(token, end);
                    end = chipEnd = Math.min(editable.length(), chipStart + token.length());
                    // Only set the span if we found a matching token.
                    if (chipStart != -1) {
                        editable.setSpan(chip, chipStart, chipEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                mRemovedSpans.clear();
            }
        }
    }

    @Override
    public void onSelectionChanged(int start, int end) {
        // When selection changes, see if it is inside the chips area.
        // If so, move the cursor back after the chips again.
        DrawableRecipientChip last = getLastChip();
        if (last != null && start < getSpannable().getSpanEnd(last)) {
            // Grab the last chip and set the cursor to after it.
            setSelection(Math.min(getSpannable().getSpanEnd(last) + 1, getText().length()));
        }
        super.onSelectionChanged(start, end);
    }

    /**
     * We cannot use the default mechanism for replaceText. Instead,
     * we override onItemClickListener so we can get all the associated
     * contact information including display text, address, and id.
     */
    @Override
    protected void replaceText(CharSequence text) {
        return;
    }

    /**
     * When an item in the suggestions list has been clicked, create a chip from the
     * contact information of the selected item.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0) {
            return;
        }
        submitItemAtPosition(position);
    }

    private void submitItemAtPosition(int position) {
        RecipientEntry entry = createValidatedEntry(getAdapter().getItem(position));
        if (entry == null) {
            return;
        }
        clearComposingText();

        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);

        Editable editable = getText();
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
        CharSequence chip = createChip(entry, false);
        if (chip != null && start >= 0 && end >= 0) {
            editable.replace(start, end, chip);
        }
        sanitizeBetween();
    }

    @Override
    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        super.setAdapter(adapter);
        ((BaseRecipientAdapter) adapter)
                .registerUpdateObserver(new BaseRecipientAdapter.EntriesUpdatedObserver() {
                    @Override
                    public void onChanged(List<RecipientEntry> entries) {
                        // Scroll the chips field to the top of the screen so
                        // that the user can see as many results as possible.
                        if (entries != null && entries.size() > 0) {
                            scrollBottomIntoView();
                        }
                    }
                });
    }

    private void scrollBottomIntoView() {
        if (mScrollView != null && mShouldShrink) {
            int[] location = new int[2];
            getLocationOnScreen(location);
            int height = getHeight();
            int currentPos = location[1] + height;
            // Desired position shows at least 1 line of chips below the action
            // bar. We add excess padding to make sure this is always below other
            // content.
            int desiredPos = (int) mChipHeight + mActionBarHeight + getExcessTopPadding();
            if (currentPos > desiredPos) {
                mScrollView.scrollBy(0, currentPos - desiredPos);
            }
        }
    }

    private int getExcessTopPadding() {
        if (sExcessTopPadding == -1) {
            sExcessTopPadding = (int) (mChipHeight + mLineSpacingExtra);
        }
        return sExcessTopPadding;
    }

    @Override
    public void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        if (width != 0 && height != 0) {
            if (mPendingChipsCount > 0) {
                postHandlePendingChips();
            } else {
                checkChipWidths();
            }
        }
        // Try to find the scroll view parent, if it exists.
        if (mScrollView == null && !mTriedGettingScrollView) {
            ViewParent parent = getParent();
            while (parent != null && !(parent instanceof ScrollView)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                mScrollView = (ScrollView) parent;
            }
            mTriedGettingScrollView = true;
        }
    }

    private void postHandlePendingChips() {
        mHandler.removeCallbacks(mHandlePendingChips);
        mHandler.post(mHandlePendingChips);
    }

    private void handlePendingChips() {
        if (getViewWidth() <= 0) {
            // The widget has not been sized yet.
            // This will be called as a result of onSizeChanged
            // at a later point.
            return;
        }
        if (mPendingChipsCount <= 0) {
            return;
        }

        synchronized (mPendingChips) {
            Editable editable = getText();
            // Tokenize!
            if (mPendingChipsCount <= MAX_CHIPS_PARSED) {
                for (int i = 0; i < mPendingChips.size(); i++) {
                    String current = mPendingChips.get(i);
                    int tokenStart = editable.toString().indexOf(current);
                    // Always leave a space at the end between tokens.
                    int tokenEnd = tokenStart + current.length() - 1;
                    if (tokenStart >= 0) {
                        // When we have a valid token, include it with the token
                        // to the left.
                        if (tokenEnd < editable.length() - 2
                                && editable.charAt(tokenEnd) == COMMIT_CHAR_COMMA) {
                            tokenEnd++;
                        }
                        createReplacementChip(tokenStart, tokenEnd, editable, i < CHIP_LIMIT
                                || !mShouldShrink);
                    }
                    mPendingChipsCount--;
                }
                sanitizeEnd();
            } else {
                mNoChips = true;
            }

            if (mTemporaryRecipients != null && mTemporaryRecipients.size() > 0
                    && mTemporaryRecipients.size() <= BaseRecipientAdapter.MAX_LOOKUPS) {
                if (hasFocus() || mTemporaryRecipients.size() < CHIP_LIMIT) {
                    new RecipientReplacementTask().execute();
                    mTemporaryRecipients = null;
                } else {
                    // Create the "more" chip
                    mIndividualReplacements = new IndividualReplacementTask();
                    mIndividualReplacements.execute(new ArrayList<>(
                            mTemporaryRecipients.subList(0, CHIP_LIMIT)));
                    if (mTemporaryRecipients.size() > CHIP_LIMIT) {
                        mTemporaryRecipients = new ArrayList<>(
                                mTemporaryRecipients.subList(CHIP_LIMIT,
                                        mTemporaryRecipients.size()));
                    } else {
                        mTemporaryRecipients = null;
                    }
                    createMoreChip();
                }
            } else {
                // There are too many recipients to look up, so just fall back
                // to showing addresses for all of them.
                mTemporaryRecipients = null;
                createMoreChip();
            }
            mPendingChipsCount = 0;
            mPendingChips.clear();
        }
    }

    private int getViewWidth() {
        return getWidth();
    }

    /**
     * Create a chip that represents just the email address of a recipient. At some later
     * point, this chip will be attached to a real contact entry, if one exists.
     */
    private void createReplacementChip(int tokenStart, int tokenEnd, Editable editable,
            boolean visible) {
        if (alreadyHasChip(tokenStart, tokenEnd)) {
            // There is already a chip present at this location.
            // Don't recreate it.
            return;
        }
        String token = editable.toString().substring(tokenStart, tokenEnd);
        final String trimmedToken = token.trim();
        int commitCharIndex = trimmedToken.lastIndexOf(COMMIT_CHAR_COMMA);
        if (commitCharIndex != -1 && commitCharIndex == trimmedToken.length() - 1) {
            token = trimmedToken.substring(0, trimmedToken.length() - 1);
        }
        RecipientEntry entry = createTokenizedEntry(token);
        if (entry != null) {
            DrawableRecipientChip chip = null;
            try {
                if (!mNoChips) {
                    chip = visible ?
                            constructChipSpan(entry, false)
                            : new InvisibleRecipientChip(entry);
                }
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            editable.setSpan(chip, tokenStart, tokenEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            // Add this chip to the list of entries "to replace"
            if (chip != null) {
                if (mTemporaryRecipients == null) {
                    mTemporaryRecipients = new ArrayList<>();
                }
                chip.setOriginalText(token);
                mTemporaryRecipients.add(chip);
            }
        }
    }

    /**
     * Remove any characters after the last valid chip.
     */
    private void sanitizeEnd() {
        // Don't sanitize while we are waiting for pending chips to complete.
        if (mPendingChipsCount > 0) {
            return;
        }
        // Find the last chip; eliminate any commit characters after it.
        DrawableRecipientChip[] chips = getSortedRecipients();
        Spannable spannable = getSpannable();
        if (chips != null && chips.length > 0) {
            int end;
            mMoreChip = getMoreChip();
            if (mMoreChip != null) {
                end = spannable.getSpanEnd(mMoreChip);
            } else {
                end = getSpannable().getSpanEnd(getLastChip());
            }
            Editable editable = getText();
            int length = editable.length();
            if (length > end) {
                // See what characters occur after that and eliminate them.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "There were extra characters after the last tokenizable entry."
                            + editable);
                }
                editable.delete(end + 1, length);
            }
        }
    }

    private class RecipientReplacementTask extends AsyncTask<Void, Void, Void> {
        private DrawableRecipientChip createFreeChip(RecipientEntry entry) {
            try {
                if (mNoChips) {
                    return null;
                }
                return constructChipSpan(entry, false);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            // Ensure everything is in chip-form already, so we don't have text that slowly gets
            // replaced
            final List<DrawableRecipientChip> originalRecipients =
                    new ArrayList<>();
            final DrawableRecipientChip[] existingChips = getSortedRecipients();
            Collections.addAll(originalRecipients, existingChips);

            if (mRemovedSpans != null) {
                originalRecipients.addAll(mRemovedSpans);
            }

            final List<DrawableRecipientChip> replacements =
                    new ArrayList<>(originalRecipients.size());

            for (final DrawableRecipientChip chip : originalRecipients) {
                if (RecipientEntry.isCreatedRecipient(chip.getEntry().getContactId())
                        && getSpannable().getSpanStart(chip) != -1) {
                    replacements.add(createFreeChip(chip.getEntry()));
                } else {
                    replacements.add(null);
                }
            }

            processReplacements(originalRecipients, replacements);
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mIndividualReplacements != null) {
                mIndividualReplacements.cancel(true);
            }
            // For each chip in the list, look up the matching contact.
            // If there is a match, replace that chip with the matching
            // chip.
            final ArrayList<DrawableRecipientChip> recipients =
                    new ArrayList<>();
            DrawableRecipientChip[] existingChips = getSortedRecipients();
            Collections.addAll(recipients, existingChips);
            if (mRemovedSpans != null) {
                recipients.addAll(mRemovedSpans);
            }
            ArrayList<String> addresses = new ArrayList<>();
            DrawableRecipientChip chip;
            for (DrawableRecipientChip recipient : recipients) {
                chip = recipient;
                if (chip != null) {
                    addresses.add(createAddressText(chip.getEntry()));
                }
            }
            final BaseRecipientAdapter adapter = getAdapter();
            BaseRecipientAdapter.getMatchingRecipients(adapter, addresses,
                    new BaseRecipientAdapter.RecipientMatchCallback() {
                @Override
                public void matchesFound(Map<String, RecipientEntry> entries) {
                    final ArrayList<DrawableRecipientChip> replacements =
                            new ArrayList<>();
                    for (final DrawableRecipientChip temp : recipients) {
                        RecipientEntry entry = null;
                        if (temp != null && RecipientEntry.isCreatedRecipient(
                                temp.getEntry().getContactId())
                                && getSpannable().getSpanStart(temp) != -1) {
                            // Replace this.
                            entry = createValidatedEntry(
                                    entries.get(tokenizeAddress(temp.getEntry()
                                            .getDestination())));
                        }
                        if (entry != null) {
                            replacements.add(createFreeChip(entry));
                        } else {
                            replacements.add(null);
                        }
                    }
                    processReplacements(recipients, replacements);
                }

                @Override
                public void matchesNotFound(final Set<String> unfoundAddresses) {
                    final List<DrawableRecipientChip> replacements =
                            new ArrayList<>(unfoundAddresses.size());

                    for (final DrawableRecipientChip temp : recipients) {
                        if (temp != null && RecipientEntry.isCreatedRecipient(
                                temp.getEntry().getContactId())
                                && getSpannable().getSpanStart(temp) != -1) {
                            if (unfoundAddresses.contains(
                                    temp.getEntry().getDestination())) {
                                replacements.add(createFreeChip(temp.getEntry()));
                            } else {
                                replacements.add(null);
                            }
                        } else {
                            replacements.add(null);
                        }
                    }

                    processReplacements(recipients, replacements);
                }
            });
            return null;
        }

        private void processReplacements(final List<DrawableRecipientChip> recipients,
                final List<DrawableRecipientChip> replacements) {
            if (replacements != null && replacements.size() > 0) {
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        final Editable text = new SpannableStringBuilder(getText());
                        int i = 0;
                        for (final DrawableRecipientChip chip : recipients) {
                            final DrawableRecipientChip replacement = replacements.get(i);
                            if (replacement != null) {
                                final RecipientEntry oldEntry = chip.getEntry();
                                final RecipientEntry newEntry = replacement.getEntry();
                                final boolean isBetter =
                                        BaseRecipientAdapter.getBetterRecipient(
                                                oldEntry, newEntry) == newEntry;

                                if (isBetter) {
                                    // Find the location of the chip in the text currently shown.
                                    final int start = text.getSpanStart(chip);
                                    if (start != -1) {
                                        // Replacing the entirety of what the chip represented,
                                        // including the extra space dividing it from other chips.
                                        final int end =
                                                Math.min(text.getSpanEnd(chip) + 1, text.length());
                                        text.removeSpan(chip);
                                        // Make sure we always have just 1 space at the end to
                                        // separate this chip from the next chip.
                                        final SpannableString displayText =
                                                new SpannableString(createAddressText(
                                                        replacement.getEntry()).trim() + " ");
                                        displayText.setSpan(replacement, 0,
                                                displayText.length() - 1,
                                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        // Replace the old text we found with with the new display
                                        // text, which now may also contain the display name of the
                                        // recipient.
                                        text.replace(start, end, displayText);
                                        replacement.setOriginalText(displayText.toString());
                                        replacements.set(i, null);

                                        recipients.set(i, replacement);
                                    }
                                }
                            }
                            i++;
                        }
                        setText(text);
                    }
                };

                if (Looper.myLooper() == Looper.getMainLooper()) {
                    runnable.run();
                } else {
                    mHandler.post(runnable);
                }
            }
        }
    }

    private RecipientEntry createValidatedEntry(RecipientEntry item) {
        if (item == null) {
            return null;
        }
        final RecipientEntry entry;
        // If the display name and the address are the same, or if this is a
        // valid contact, but the destination is invalid, then make this a fake
        // recipient that is editable.
        String destination = item.getDestination();
        if (item.getContactId() == RecipientEntry.GENERATED_CONTACT) {
            entry = RecipientEntry.constructGeneratedEntry(item.getDisplayName(),
                    destination, item.isValid());
        } else if (RecipientEntry.isCreatedRecipient(item.getContactId())
                && (TextUtils.isEmpty(item.getDisplayName())
                || TextUtils.equals(item.getDisplayName(), destination)
                || (mValidator != null && !mValidator.isValid(destination)))) {
            entry = RecipientEntry.constructFakeEntry(destination, item.isValid());
        } else {
            entry = item;
        }
        return entry;
    }

    private static String tokenizeAddress(String destination) {
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(destination);
        if (tokens.length > 0) {
            return tokens[0].getAddress();
        }
        return destination;
    }

    private class IndividualReplacementTask
            extends AsyncTask<ArrayList<DrawableRecipientChip>, Void, Void> {
        @Override
        protected Void doInBackground(ArrayList<DrawableRecipientChip>... params) {
            // For each chip in the list, look up the matching contact.
            // If there is a match, replace that chip with the matching
            // chip.
            final ArrayList<DrawableRecipientChip> originalRecipients = params[0];
            ArrayList<String> addresses = new ArrayList<>();
            DrawableRecipientChip chip;
            for (DrawableRecipientChip originalRecipient : originalRecipients) {
                chip = originalRecipient;
                if (chip != null) {
                    addresses.add(createAddressText(chip.getEntry()));
                }
            }
            final BaseRecipientAdapter adapter = getAdapter();
            BaseRecipientAdapter.getMatchingRecipients(adapter, addresses,
                    new BaseRecipientAdapter.RecipientMatchCallback() {

                        @Override
                        public void matchesFound(Map<String, RecipientEntry> entries) {
                            for (final DrawableRecipientChip temp : originalRecipients) {
                                if (RecipientEntry.isCreatedRecipient(temp.getEntry()
                                        .getContactId())
                                        && getSpannable().getSpanStart(temp) != -1) {
                                    // Replace this.
                                    final RecipientEntry entry = createValidatedEntry(entries
                                            .get(tokenizeAddress(temp.getEntry().getDestination())
                                                    .toLowerCase()));
                                    if (entry != null) {
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                replaceChip(temp, entry);
                                            }
                                        });
                                    }
                                }
                            }
                        }

                        @Override
                        public void matchesNotFound(final Set<String> unfoundAddresses) {
                            // No action required
                        }
                    });
            return null;
        }
    }

    private ImageSpan getMoreChip() {
        MoreImageSpan[] moreSpans = getSpannable().getSpans(0, getText().length(),
                MoreImageSpan.class);
        return moreSpans != null && moreSpans.length > 0 ? moreSpans[0] : null;
    }

    /**
     * MoreImageSpan is a simple class created for tracking the existence of a
     * more chip across activity restarts/
     */
    private class MoreImageSpan extends ImageSpan {
        public MoreImageSpan(Drawable b) {
            super(b);
        }
    }

    private DrawableRecipientChip getLastChip() {
        DrawableRecipientChip last = null;
        DrawableRecipientChip[] chips = getSortedRecipients();
        if (chips != null && chips.length > 0) {
            last = chips[chips.length - 1];
        }
        return last;
    }

    /**
     * Create the more chip. The more chip is text that replaces any chips that
     * do not fit in the pre-defined available space when the
     * RecipientEditTextView loses focus.
     */
    private void createMoreChip() {
        if (mNoChips) {
            createMoreChipPlainText();
            return;
        }

        if (!mShouldShrink) {
            return;
        }
        ImageSpan[] tempMore = getSpannable().getSpans(0, getText().length(), MoreImageSpan.class);
        if (tempMore.length > 0) {
            getSpannable().removeSpan(tempMore[0]);
        }
        DrawableRecipientChip[] recipients = getSortedRecipients();

        if (recipients == null || recipients.length <= CHIP_LIMIT) {
            mMoreChip = null;
            return;
        }
        Spannable spannable = getSpannable();
        int numRecipients = recipients.length;
        int overage = numRecipients - CHIP_LIMIT;
        MoreImageSpan moreSpan = createMoreSpan(overage);
        mRemovedSpans = new ArrayList<>();
        int totalReplaceStart = 0;
        int totalReplaceEnd = 0;
        Editable text = getText();
        for (int i = numRecipients - overage; i < recipients.length; i++) {
            mRemovedSpans.add(recipients[i]);
            if (i == numRecipients - overage) {
                totalReplaceStart = spannable.getSpanStart(recipients[i]);
            }
            if (i == recipients.length - 1) {
                totalReplaceEnd = spannable.getSpanEnd(recipients[i]);
            }
            if (mTemporaryRecipients == null || !mTemporaryRecipients.contains(recipients[i])) {
                int spanStart = spannable.getSpanStart(recipients[i]);
                int spanEnd = spannable.getSpanEnd(recipients[i]);
                recipients[i].setOriginalText(text.toString().substring(spanStart, spanEnd));
            }
            spannable.removeSpan(recipients[i]);
        }
        if (totalReplaceEnd < text.length()) {
            totalReplaceEnd = text.length();
        }
        int end = Math.max(totalReplaceStart, totalReplaceEnd);
        int start = Math.min(totalReplaceStart, totalReplaceEnd);
        SpannableString chipText = new SpannableString(text.subSequence(start, end));
        chipText.setSpan(moreSpan, 0, chipText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.replace(start, end, chipText);
        mMoreChip = moreSpan;
        // If adding the +more chip goes over the limit, resize accordingly.
        if (getLineCount() > mMaxLines) {
            setMaxLines(getLineCount());
        }
    }

    private void createMoreChipPlainText() {
        // Take the first <= CHIP_LIMIT addresses and get to the end of the second one.
        Editable text = getText();
        int start = 0;
        int end = start;
        for (int i = 0; i < CHIP_LIMIT; i++) {
            end = movePastTerminators(mTokenizer.findTokenEnd(text, start));
            start = end; // move to the next token and get its end.
        }
        // Now, count total addresses.
        int tokenCount = countTokens(text);
        MoreImageSpan moreSpan = createMoreSpan(tokenCount - CHIP_LIMIT);
        SpannableString chipText = new SpannableString(text.subSequence(end, text.length()));
        chipText.setSpan(moreSpan, 0, chipText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.replace(end, text.length(), chipText);
        mMoreChip = moreSpan;
    }

    private int countTokens(Editable text) {
        int tokenCount = 0;
        int start = 0;
        while (start < text.length()) {
            start = movePastTerminators(mTokenizer.findTokenEnd(text, start));
            tokenCount++;
            if (start >= text.length()) {
                break;
            }
        }
        return tokenCount;
    }

    private MoreImageSpan createMoreSpan(int count) {
        String moreText = String.format(mMoreItem.getText().toString(), count);
        TextPaint morePaint = new TextPaint(getPaint());
        morePaint.setTextSize(mMoreItem.getTextSize());
        morePaint.setColor(mMoreItem.getCurrentTextColor());
        int width = (int)morePaint.measureText(moreText) + mMoreItem.getPaddingLeft()
                + mMoreItem.getPaddingRight();
        int height = getLineHeight();
        Bitmap drawable = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(drawable);
        int adjustedHeight = height;
        Layout layout = getLayout();
        if (layout != null) {
            adjustedHeight -= layout.getLineDescent(0);
        }
        canvas.drawText(moreText, 0, moreText.length(), 0, adjustedHeight, morePaint);

        Drawable result = new BitmapDrawable(getResources(), drawable);
        result.setBounds(0, 0, width, height);
        return new MoreImageSpan(result);
    }

    private void checkChipWidths() {
        // Check the widths of the associated chips.
        DrawableRecipientChip[] chips = getSortedRecipients();
        if (chips != null) {
            Rect bounds;
            for (DrawableRecipientChip chip : chips) {
                bounds = chip.getBounds();
                if (getWidth() > 0 && bounds.right - bounds.left > getWidth()) {
                    // Need to redraw that chip.
                    replaceChip(chip, chip.getEntry());
                }
            }
        }
    }

    /**
     * Replace this currently selected chip with a new chip
     * that uses the contact data provided.
     */
    private void replaceChip(DrawableRecipientChip chip, RecipientEntry entry) {
        boolean wasSelected = chip == mSelectedChip;
        if (wasSelected) {
            mSelectedChip = null;
        }
        int start = getChipStart(chip);
        int end = getChipEnd(chip);
        getSpannable().removeSpan(chip);
        Editable editable = getText();
        CharSequence chipText = createChip(entry, false);
        if (chipText != null) {
            if (start == -1 || end == -1) {
                Log.e(TAG, "The chip to replace does not exist but should.");
                editable.insert(0, chipText);
            } else {
                if (!TextUtils.isEmpty(chipText)) {
                    // There may be a space to replace with this chip's new
                    // associated space. Check for it
                    int toReplace = end;
                    while (toReplace >= 0 && toReplace < editable.length()
                            && editable.charAt(toReplace) == ' ') {
                        toReplace++;
                    }
                    editable.replace(start, toReplace, chipText);
                }
            }
        }
        setCursorVisible(true);
        if (wasSelected) {
            clearSelectedChip();
        }
    }

    private int getChipStart(DrawableRecipientChip chip) {
        return getSpannable().getSpanStart(chip);
    }

    private int getChipEnd(DrawableRecipientChip chip) {
        return getSpannable().getSpanEnd(chip);
    }

    private void clearSelectedChip() {
        if (mSelectedChip != null) {
            unselectChip(mSelectedChip);
            mSelectedChip = null;
        }
        setCursorVisible(true);
    }

    /**
     * Remove selection from this chip. Unselecting a RecipientChip will render
     * the chip without a delete icon and with an unfocused background. This is
     * called when the RecipientChip no longer has focus.
     */
    private void unselectChip(DrawableRecipientChip chip) {
        int start = getChipStart(chip);
        int end = getChipEnd(chip);
        Editable editable = getText();
        mSelectedChip = null;
        if (start == -1 || end == -1) {
            Log.w(TAG, "The chip doesn't exist or may be a chip a user was editing");
            setSelection(editable.length());
            commitDefault();
        } else {
            getSpannable().removeSpan(chip);
            QwertyKeyListener.markAsReplaced(editable, start, end, "");
            editable.removeSpan(chip);
            try {
                if (!mNoChips) {
                    editable.setSpan(constructChipSpan(chip.getEntry(), false),
                            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        setCursorVisible(true);
        setSelection(editable.length());
        // TODO: use Compat ListPopupWindow
        if (mAlternatesPopup != null && mAlternatesPopup.isShowing()) {
            mAlternatesPopup.dismiss();
        }
    }

    /**
     * Create a chip from the default selection. If the popup is showing, the
     * default is the first item in the popup suggestions list. Otherwise, it is
     * whatever the user had typed in. End represents where the the tokenizer
     * should search for a token to turn into a chip.
     * @return If a chip was created from a real contact.
     */
    private boolean commitDefault() {
        // If there is no tokenizer, don't try to commit.
        if (mTokenizer == null) {
            return false;
        }
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(editable, end);

        if (shouldCreateChip(start, end)) {
            int whatEnd = mTokenizer.findTokenEnd(getText(), start);
            // In the middle of chip; treat this as an edit
            // and commit the whole token.
            whatEnd = movePastTerminators(whatEnd);
            if (whatEnd != getSelectionEnd()) {
                handleEdit(start, whatEnd);
                return true;
            }
            return commitChip(start, end , editable);
        }
        return false;
    }

    private int movePastTerminators(int tokenEnd) {
        if (tokenEnd >= length()) {
            return tokenEnd;
        }
        char atEnd = getText().toString().charAt(tokenEnd);
        if (atEnd == COMMIT_CHAR_COMMA || atEnd == COMMIT_CHAR_SEMICOLON) {
            tokenEnd++;
        }
        // This token had not only an end token character, but also a space
        // separating it from the next token.
        if (tokenEnd < length() && getText().toString().charAt(tokenEnd) == ' ') {
            tokenEnd++;
        }
        return tokenEnd;
    }

    private void handleEdit(int start, int end) {
        if (start == -1 || end == -1) {
            // This chip no longer exists in the field.
            dismissDropDown();
            return;
        }
        // This is in the middle of a chip, so select out the whole chip
        // and commit it.
        Editable editable = getText();
        setSelection(end);
        String text = getText().toString().substring(start, end);
        if (!TextUtils.isEmpty(text)) {
            RecipientEntry entry = RecipientEntry.constructFakeEntry(text, isValid(text));
            QwertyKeyListener.markAsReplaced(editable, start, end, "");
            CharSequence chipText = createChip(entry, false);
            int selEnd = getSelectionEnd();
            if (chipText != null && start > -1 && selEnd > -1) {
                editable.replace(start, selEnd, chipText);
            }
        }
        dismissDropDown();
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

    @Override
    public void performValidation() {
        // Do nothing. Chips handles its own validation.
    }

    private class RecipientTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Do nothing.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // The user deleted some text OR some text was replaced; check to
            // see if the insertion point is on a space
            // following a chip.
            if (before - count == 1) {
                // If the item deleted is a space, and the thing before the
                // space is a chip, delete the entire span.
                int selStart = getSelectionStart();
                DrawableRecipientChip[] recipients = getSpannable().getSpans(selStart, selStart,
                        DrawableRecipientChip.class);
                if (recipients.length > 0) {
                    // There is a chip there! Just remove it.
                    Editable editable = getText();
                    // Add the separator token.
                    int tokenStart = mTokenizer.findTokenStart(editable, selStart);
                    int tokenEnd = mTokenizer.findTokenEnd(editable, tokenStart);
                    tokenEnd = tokenEnd + 1;
                    if (tokenEnd > editable.length()) {
                        tokenEnd = editable.length();
                    }
                    editable.delete(tokenStart, tokenEnd);
                    getSpannable().removeSpan(recipients[0]);
                }
            } else if (count > before) {
                if (mSelectedChip != null
                        && isGeneratedContact(mSelectedChip)) {
                    if (lastCharacterIsCommitCharacter(s)) {
                        commitByCharacter();
                        return;
                    }
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            // If the text has been set to null or empty, make sure we remove
            // all the spans we applied.
            if (TextUtils.isEmpty(s)) {
                // Remove all the chips spans.
                Spannable spannable = getSpannable();
                DrawableRecipientChip[] chips = spannable.getSpans(0, getText().length(),
                        DrawableRecipientChip.class);
                for (DrawableRecipientChip chip : chips) {
                    spannable.removeSpan(chip);
                }
                if (mMoreChip != null) {
                    spannable.removeSpan(mMoreChip);
                }
                return;
            }
            // Get whether there are any recipients pending addition to the
            // view. If there are, don't do anything in the text watcher.
            if (chipsPending()) {
                return;
            }
            // If the user is editing a chip, don't clear it.
            if (mSelectedChip != null) {
                if (!isGeneratedContact(mSelectedChip)) {
                    setCursorVisible(true);
                    setSelection(getText().length());
                    clearSelectedChip();
                } else {
                    return;
                }
            }
            int length = s.length();
            // Make sure there is content there to parse and that it is
            // not just the commit character.
            if (length > 1) {
                if (lastCharacterIsCommitCharacter(s)) {
                    commitByCharacter();
                    return;
                }
                char last;
                int end = getSelectionEnd() == 0 ? 0 : getSelectionEnd() - 1;
                int len = length() - 1;
                if (end != len) {
                    last = s.charAt(end);
                } else {
                    last = s.charAt(len);
                }
                if (last == COMMIT_CHAR_SPACE) {
                    // Check if this is a valid email address. If it is,
                    // commit it.
                    String text = getText().toString();
                    int tokenStart = mTokenizer.findTokenStart(text, getSelectionEnd());
                    String sub = text.substring(tokenStart, mTokenizer.findTokenEnd(text,
                            tokenStart));
                    if (!TextUtils.isEmpty(sub) && mValidator != null &&
                            mValidator.isValid(sub)) {
                        commitByCharacter();
                    }
                }
            }
        }

        private boolean chipsPending() {
            return mPendingChipsCount > 0 || (mRemovedSpans != null && mRemovedSpans.size() > 0);
        }

        public boolean isGeneratedContact(DrawableRecipientChip chip) {
            long contactId = chip.getContactId();
            return contactId == RecipientEntry.INVALID_CONTACT || contactId == RecipientEntry.GENERATED_CONTACT;
        }

        private void clearSelectedChip() {
            if (mSelectedChip != null) {
                unselectChip(mSelectedChip);
                mSelectedChip = null;
            }
            setCursorVisible(true);
        }

        /**
         * Remove selection from this chip. Unselecting a RecipientChip will render
         * the chip without a delete icon and with an unfocused background. This is
         * called when the RecipientChip no longer has focus.
         */
        private void unselectChip(DrawableRecipientChip chip) {
            int start = getChipStart(chip);
            int end = getChipEnd(chip);
            Editable editable = getText();
            mSelectedChip = null;
            if (start == -1 || end == -1) {
                Log.w(TAG, "The chip doesn't exist or may be a chip a user was editing");
                setSelection(editable.length());
                commitDefault();
            } else {
                getSpannable().removeSpan(chip);
                QwertyKeyListener.markAsReplaced(editable, start, end, "");
                editable.removeSpan(chip);
                try {
                    if (!mNoChips) {
                        editable.setSpan(constructChipSpan(chip.getEntry(), false),
                                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } catch (NullPointerException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            setCursorVisible(true);
            setSelection(editable.length());
            // TODO: use compatible ListPopupWindow
            if (mAlternatesPopup != null && mAlternatesPopup.isShowing()) {
                mAlternatesPopup.dismiss();
            }
        }

        private int getChipStart(DrawableRecipientChip chip) {
            return getSpannable().getSpanStart(chip);
        }

        private int getChipEnd(DrawableRecipientChip chip) {
            return getSpannable().getSpanEnd(chip);
        }

        /**
         * Create a chip from the default selection. If the popup is showing, the
         * default is the first item in the popup suggestions list. Otherwise, it is
         * whatever the user had typed in. End represents where the the tokenizer
         * should search for a token to turn into a chip.
         * @return If a chip was created from a real contact.
         */
        private boolean commitDefault() {
            // If there is no tokenizer, don't try to commit.
            if (mTokenizer == null) {
                return false;
            }
            Editable editable = getText();
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(editable, end);

            if (shouldCreateChip(start, end)) {
                int whatEnd = mTokenizer.findTokenEnd(getText(), start);
                // In the middle of chip; treat this as an edit
                // and commit the whole token.
                whatEnd = movePastTerminators(whatEnd);
                if (whatEnd != getSelectionEnd()) {
                    handleEdit(start, whatEnd);
                    return true;
                }
                return commitChip(start, end , editable);
            }
            return false;
        }

        public int movePastTerminators(int tokenEnd) {
            if (tokenEnd >= length()) {
                return tokenEnd;
            }
            char atEnd = getText().toString().charAt(tokenEnd);
            if (atEnd == COMMIT_CHAR_COMMA || atEnd == COMMIT_CHAR_SEMICOLON) {
                tokenEnd++;
            }
            // This token had not only an end token character, but also a space
            // separating it from the next token.
            if (tokenEnd < length() && getText().toString().charAt(tokenEnd) == ' ') {
                tokenEnd++;
            }
            return tokenEnd;
        }

        private void handleEdit(int start, int end) {
            if (start == -1 || end == -1) {
                // This chip no longer exists in the field.
                dismissDropDown();
                return;
            }
            // This is in the middle of a chip, so select out the whole chip
            // and commit it.
            Editable editable = getText();
            setSelection(end);
            String text = getText().toString().substring(start, end);
            if (!TextUtils.isEmpty(text)) {
                RecipientEntry entry = RecipientEntry.constructFakeEntry(text, isValid(text));
                QwertyKeyListener.markAsReplaced(editable, start, end, "");
                CharSequence chipText = createChip(entry, false);
                int selEnd = getSelectionEnd();
                if (chipText != null && start > -1 && selEnd > -1) {
                    editable.replace(start, selEnd, chipText);
                }
            }
            dismissDropDown();
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
            submitItemAtPosition(0);
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

    @Override
    public BaseRecipientAdapter getAdapter() {
        return (BaseRecipientAdapter) super.getAdapter();
    }
}
