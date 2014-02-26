package sk.rajniak.chips.sample;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.util.Rfc822Tokenizer;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

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
            protected HashMap<String, RecipientEntry> getMatchingRecipients(HashSet<String> inAddresses) {
                final HashMap<String, RecipientEntry> test = new HashMap<>();
                int i = 2;
                for (String inAddress : inAddresses) {
                    test.put(inAddress,
                            RecipientEntry.constructTopLevelEntry("test" + i, "test" + i + "@gmail.com", i, true));
                }
                return test;
            }

            @Override
            protected HashMap<String, List<RecipientEntry>> getMatchingRecipients(CharSequence constraint) {
                final HashMap<String, List<RecipientEntry>> test = new HashMap<>();
                final ArrayList<RecipientEntry> testList = new ArrayList<>();
                testList.add(RecipientEntry.constructTopLevelEntry("test1", "test1@gmail.com", 1, true));
                testList.add(RecipientEntry.constructTopLevelEntry("test2", "test2@gmail.com", 2, true));
                testList.add(RecipientEntry.constructTopLevelEntry("test3", "test3@gmail.com", 3, true));
                testList.add(RecipientEntry.constructTopLevelEntry("test4", "test4@gmail.com", 4, true));
                test.put(constraint.toString(), testList);
                return test;
            }
        });
    }
}
