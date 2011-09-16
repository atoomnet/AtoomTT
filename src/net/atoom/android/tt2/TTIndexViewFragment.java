package net.atoom.android.tt2;

import java.util.StringTokenizer;

import net.atoom.android.tt2.util.LogBridge;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipData.Item;
import android.os.Bundle;
import android.view.DragEvent;
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

//		button.setOnLongClickListener(new OnLongClickListener() {
//
//			@Override
//			public boolean onLongClick(View arg0) {
//				ClipData data = ClipData.newPlainText("hello", "world");
//				arg0.startDrag(data, new View.DragShadowBuilder(arg0), null, 0);
//
//				return true;
//			}
//		});
//
//		button.setOnDragListener(new OnDragListener() {
//			public boolean onDrag(View v, DragEvent event) {
//				if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
//					return processDragStared(event);
//				else if (event.getAction() == DragEvent.ACTION_DROP)
//					return processDrop(event, button);
//
//				return false;
//			}
//		});
		return button;
	}

	protected boolean processDrop(DragEvent event, Button lv) {
		ClipData data = event.getClipData();
		if (data != null) {
			if (data.getItemCount() > 0) {
				Item item = data.getItemAt(0);
				String textData = (String) item.getText();
				if (textData != null) {
					StringTokenizer tokenizer = new StringTokenizer(textData, "||");
					if (tokenizer.countTokens() != 2) {
						return false;
					}
					int category = -1;
					int entryId = -1;
					try {
						category = Integer.parseInt(tokenizer.nextToken());
						entryId = Integer.parseInt(tokenizer.nextToken());
					} catch (NumberFormatException exception) {
						return false;
					}

					LogBridge.i("I got a drop baaaaaaaaaaaaaaaaaa!");
					return true;

				}
			}
		}

		return false;
	}

	protected boolean processDragStared(DragEvent event) {
		// TODO Auto-generated method stub
		ClipDescription clipDesc = event.getClipDescription();
		if (clipDesc != null) {
			return clipDesc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
		}
		return false;
	}
}
