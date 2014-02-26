package sk.rajniak.chips.sample;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.util.Rfc822Tokenizer;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import sk.rajniak.chips.RecipientEditTextView;
import sk.rajniak.chips.model.BaseRecipientAdapter;
import sk.rajniak.chips.model.RecipientEntry;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final RecipientEditTextView recipientTv = (RecipientEditTextView) findViewById(R.id.recipient_tv);
        recipientTv.setTokenizer(new Rfc822Tokenizer());
        recipientTv.setAdapter(new BaseRecipientAdapter(this){

            @Override
            public List<RecipientEntry> getAlternativeRecipients(String displayName) {
                List<RecipientEntry> alternatives = new ArrayList<>();
                if (displayName.equals("test1")) {
                    alternatives.add(RecipientEntry.constructTopLevelEntry("test1", "test1@gmail.com", 1, true));
                    alternatives.add(RecipientEntry.constructTopLevelEntry("test1", "test1@foundation.com", 11, true));
                }
                return alternatives;
            }

            @Override
            public HashMap<String, List<RecipientEntry>> getAlternativeRecipients(HashSet<String> displayNames) {
                final HashMap<String, List<RecipientEntry>> test = new HashMap<>();
                for (String displayName : displayNames) {
                    if (displayName.equals("test1")) {
                        List<RecipientEntry> alternatives = new ArrayList<>();
                        alternatives.add(RecipientEntry.constructTopLevelEntry("test1", "test1@gmail.com", 1, true));
                        alternatives.add(RecipientEntry.constructTopLevelEntry("test1", "test1@foundation.com", 11, true));
                        test.put(displayName, alternatives);
                    }
                }
                return test;
            }

            @Override
            protected HashMap<String, List<RecipientEntry>> getMatchingRecipients(CharSequence constraint) {
                final HashMap<String, List<RecipientEntry>> test = new HashMap<>();
                final ArrayList<RecipientEntry> testList = new ArrayList<>();

                if ("test".contains(constraint.toString())) {
                    testList.add(RecipientEntry.constructTopLevelEntry("test1", "test1@gmail.com", 1, true));
                    testList.add(RecipientEntry.constructTopLevelEntry("test2", "test2@gmail.com", 2, true));
                    testList.add(RecipientEntry.constructTopLevelEntry("test3", "test3@gmail.com", 3, true));
                    testList.add(RecipientEntry.constructTopLevelEntry("test4", "test4@gmail.com", 4, true));
                }
                test.put(constraint.toString(), testList);
                return test;
            }
        });

        final Button appendButton = (Button)findViewById(R.id.append);
        appendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recipientTv.append("test1");
            }
        });
    }
}
