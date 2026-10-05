package com.example.six_sense.ui.devices;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.example.six_sense.R;
import com.example.six_sense.data.FridgeInventory;
import com.example.six_sense.databinding.ItemDeviceGlanceBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceType;
import com.example.six_sense.ui.DeviceIcons;

/**
 * 한눈에 모드 기기 목록. 한 줄에 하나.
 * 카드 터치 = 상세 화면, 오른쪽 큰 버튼 = 전원, 별 = 홈 즐겨찾기.
 */
public class GlanceDeviceAdapter extends RecyclerView.Adapter<GlanceDeviceAdapter.Holder> {

    public interface Callbacks {
        void onOpen(Device device);
        /** 스위치를 밀었을 때. 실제 반영 여부는 PowerControl 규칙에 따른다 */
        void onPowerRequest(Device device, boolean wantOn);
        void onFavorite(Device device);
        boolean isFavorite(Device device);
    }

    private static final float OFF_ALPHA = 0.55f;

    private final List<Device> items = new ArrayList<>();
    private final Callbacks callbacks;

    public GlanceDeviceAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    public void submit(List<Device> devices) {
        items.clear();
        items.addAll(devices);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDeviceGlanceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemDeviceGlanceBinding b;

        Holder(ItemDeviceGlanceBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Device d) {
            int rose = itemView.getContext().getColor(R.color.rose);
            int muted = itemView.getContext().getColor(R.color.muted);

            b.textName.setText(d.getName());
            b.textState.setText(d.describeState());
            b.imageIcon.setImageResource(DeviceIcons.of(d.getType()));
            b.imageIcon.setImageTintList(ColorStateList.valueOf(d.isToggleable() && d.isOn() ? rose : muted));

            boolean fav = callbacks.isFavorite(d);
            b.buttonFavorite.setImageResource(fav ? R.drawable.ic_star_filled : R.drawable.ic_star);
            b.buttonFavorite.setImageTintList(ColorStateList.valueOf(fav ? rose : muted));
            b.buttonFavorite.setOnClickListener(v -> callbacks.onFavorite(d));

            boolean useSwitch = d.isToggleable() && d.getType() != DeviceType.BLIND;
            b.switchPower.setVisibility(useSwitch ? View.VISIBLE : View.GONE);
            b.buttonPower.setVisibility(useSwitch ? View.GONE : View.VISIBLE);

            if (useSwitch) {
                // 재활용되는 뷰라 애니메이션 없이 바로 맞춘다
                b.switchPower.setOn(d.isOn(), false);
                b.switchPower.setContentDescription(
                        itemView.getContext().getString(R.string.device_toggle_desc, d.getName()));
                b.switchPower.setListener(want -> callbacks.onPowerRequest(d, want));
            } else if (d.getType() == DeviceType.BLIND) {
                // 블라인드는 ON/OFF가 아니라 열린 정도. 누르면 상세 화면에서 조절
                b.buttonPower.setText(d.getValue() + "%");
                b.buttonPower.setTextSize(20f);
                b.buttonPower.setBackgroundResource(R.drawable.bg_button_secondary);
                b.buttonPower.setAlpha(1f);
                b.buttonPower.setTextColor(itemView.getContext().getColor(R.color.ink));
                b.buttonPower.setOnClickListener(v -> callbacks.onOpen(d));
            } else {
                // 냉장고: 켜고 끌 게 없으니 상태(정상 / 임박)를 보여준다
                boolean warn = FridgeInventory.hasExpiring();
                b.buttonPower.setText(FridgeInventory.statusLabel());
                b.buttonPower.setTextSize(warn ? 15f : 20f);
                b.buttonPower.setBackgroundResource(warn ? R.drawable.bg_state_warn : R.drawable.bg_state_on);
                b.buttonPower.setAlpha(1f);
                b.buttonPower.setTextColor(itemView.getContext().getColor(R.color.white));
                b.buttonPower.setOnClickListener(v -> callbacks.onOpen(d));
            }
            b.cardRoot.setOnClickListener(v -> callbacks.onOpen(d));
        }
    }
}
