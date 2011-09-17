package net.atoom.android.tt2;

import android.app.Fragment;
import android.content.ClipData;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

public class TTIndexViewFragment extends Fragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.fragment_index, container, false);
		view.addView(createButton("Journaal", "http://teletekst.nos.nl/tekst/101-01.html"));
		view.addView(createButton("Binnenland", "http://teletekst.nos.nl/tekst/102-01.html"));
		view.addView(createButton("Buitenland", "http://teletekst.nos.nl/tekst/103-01.html"));
		view.addView(createButton("Opmerkelijk", "http://teletekst.nos.nl/tekst/401-01.html"));
		view.addView(createButton("Financieel", "http://teletekst.nos.nl/tekst/501-01.html"));
		view.addView(createButton("Sport", "http://teletekst.nos.nl/tekst/601-01.html"));
		view.addView(createButton("Voetbal", "http://teletekst.nos.nl/tekst/801-01.html"));
		view.addView(createButton("Weer/Verkeer", "http://teletekst.nos.nl/tekst/701-01.html"));
		view.addView(createButton("Gids", "http://teletekst.nos.nl/tekst/200-01.html"));
		return view;
	}

	private Button createButton(final String title, final String url) {
		final Button button = new Button(getActivity());
		button.setWidth(400);
		button.setMaxWidth(400);
		button.setText(title);

		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				TTMainViewFragment mainfragment = (TTMainViewFragment) getActivity().getFragmentManager()
						.findFragmentById(R.id.mainfragment);
				if (mainfragment != null)
					mainfragment.loadPageUrl(url, true);
			}
		});

		button.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				v.startDrag(ClipData.newPlainText("url", url), new View.DragShadowBuilder(v), null, 0);
				return true;
			}
		});
		return button;
	}
}