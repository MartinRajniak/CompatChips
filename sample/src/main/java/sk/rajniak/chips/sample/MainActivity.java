package sk.rajniak.chips.sample;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import sk.rajniak.chips.RecipientEditTextView;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final RecipientEditTextView recipientTv = (RecipientEditTextView) findViewById(R.id.recipient_tv);

    }
}
