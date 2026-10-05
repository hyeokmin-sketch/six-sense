package com.example.six_sense.ui.devices;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.example.six_sense.R;
import com.example.six_sense.databinding.ItemDeviceBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.ui.DeviceIcons;

/**
 * 기기 카드 그리드. 카드 하나에 기기 하나, 토글 하나.
 */
public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.Holder> {

    public interface OnToggle {
        void onToggle(Device device);
    }

    private final List<Device> items = new ArrayList<>();
    private final OnToggle onToggle;

    public DeviceAdapter(OnToggle onToggle) {
        this.onToggle = onToggle;
        setHasStableIds(true);
    }

    public void submit(List<Device> devices) {
        items.clear();
        items.addAll(devices);
        notifyDataSetChanged();   // 9개뿐이라 DiffUtil까지는 안 씀
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeviceBinding b = ItemDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemDeviceBinding b;

        Holder(ItemDeviceBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Device d) {
            b.textName.setText(d.getName());
            b.textState.setText(d.describeState());
            b.imageIcon.setImageResource(DeviceIcons.of(d.getType()));

            boolean on = d.isToggleable() && d.isOn();
            int accent = itemView.getContext().getColor(R.color.rose);
            int muted = itemView.getContext().getColor(R.color.muted);

            // 켜짐: 테두리 + 아이콘 + 상태 글자 색을 같이 바꾼다 (색만으로 구분하지 않도록 글자도 바뀜)
            b.cardRoot.setBackgroundResource(on ? R.drawable.bg_card_accent : R.drawable.bg_card);
            b.imageIcon.setImageTintList(android.content.res.ColorStateList.valueOf(on ? accent : muted));
            b.textState.setTextColor(on ? accent : muted);
            b.textState.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            if (d.isToggleable()) {
                b.switchPower.setVisibility(View.VISIBLE);
                b.imageChevron.setVisibility(View.GONE);
                // 리스너를 먼저 떼야 setChecked가 콜백을 안 부른다
                b.switchPower.setOnCheckedChangeListener(null);
                b.switchPower.setChecked(d.isOn());
                b.switchPower.setContentDescription(
                        itemView.getContext().getString(R.string.device_toggle_desc, d.getName()));
                b.switchPower.setOnCheckedChangeListener((v, checked) -> onToggle.onToggle(d));
                // 카드 아무 데나 눌러도 토글되게. 버튼이 작아서 어르신들이 못 누르는 경우 대비
                b.cardRoot.setOnClickListener(v -> b.switchPower.toggle());
            } else {
                b.switchPower.setVisibility(View.GONE);
                b.imageChevron.setVisibility(View.VISIBLE);
                b.cardRoot.setOnClickListener(v -> onToggle.onToggle(d));
            }
        }
    }
}
