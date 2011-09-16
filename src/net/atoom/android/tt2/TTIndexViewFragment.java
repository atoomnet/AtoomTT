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
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class TTIndexViewFragment extends Fragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.fragment_index, container, false);

		TextView tv1 = (TextView) view.findViewById(R.id.tv1);
		tv1.setOnDragListener(new OnDragListener() {
			public boolean onDrag(View v, DragEvent event) {
				if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
					LogBridge.i("I got a drop started!");
				if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
					LogBridge.i("I got a drop ended!");
				if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED)
					LogBridge.i("I got a drop entered!");
				if (event.getAction() == DragEvent.ACTION_DRAG_EXITED)
					LogBridge.i("I got a drop exited!");
				if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION)
					LogBridge.i("I got a drop location!");
				return false;
			}
		});

		final Button button = new Button(getActivity());
		// button.setText(" Hello Atoom");
		button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				final TTMainViewFragment mainview = (TTMainViewFragment) getActivity().getFragmentManager()
						.findFragmentById(R.id.mainfragment);
				mainview.loadNextPage();
			}
		});

		button.setOnLongClickListener(new OnLongClickListener() {

			@Override
			public boolean onLongClick(View arg0) {
				ClipData data = ClipData.newPlainText("hello", "world");
				arg0.startDrag(data, new View.DragShadowBuilder(arg0), null, 0);

				return true;
			}
		});

		button.setOnDragListener(new OnDragListener() {
			public boolean onDrag(View v, DragEvent event) {
				if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
					return processDragStared(event);
				else if (event.getAction() == DragEvent.ACTION_DROP)
					return processDrop(event, button);

				return false;
			}
		});
		view.addView(button);
		return view;
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
