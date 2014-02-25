package sk.rajniak.chips;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.MultiAutoCompleteTextView;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView {

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
