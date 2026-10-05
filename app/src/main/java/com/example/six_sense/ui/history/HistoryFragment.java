package com.example.six_sense.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import com.example.six_sense.R;
import com.example.six_sense.data.HistoryRepository;
import com.example.six_sense.databinding.DialogCorrectionBinding;
import com.example.six_sense.databinding.FragmentHistoryBinding;
import com.example.six_sense.model.HistoryEntry;

/**
 * 화면 05. 대화 기록 · 교정.
 * "잘못됐어요" → 어떻게 했어야 하는지 한 줄 입력 → HistoryRepository에 교정으로 저장.
 */
public class HistoryFragment extends Fragment implements HistoryRepository.Listener {

    private FragmentHistoryBinding binding;
    private final HistoryRepository history = HistoryRepository.getInstance();
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new HistoryAdapter(this::showCorrectionDialog);
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerHistory.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        history.addListener(this);
        refresh();
    }

    @Override
    public void onStop() {
        history.removeListener(this);
        super.onStop();
    }

    @Override
    public void onHistoryChanged() {
        if (binding == null) return;
        binding.getRoot().post(this::refresh);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void refresh() {
        List<HistoryEntry> all = history.getAll();
        adapter.submit(all);
        binding.textEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerHistory.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showCorrectionDialog(HistoryEntry entry) {
        DialogCorrectionBinding d = DialogCorrectionBinding.inflate(getLayoutInflater());
        d.textHeard.setText("“" + entry.getHeard() + "” → " + entry.getActionSummary());

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.correction_title)
                .setView(d.getRoot())
                .setNegativeButton(R.string.correction_cancel, null)
                .setPositiveButton(R.string.correction_save, (dlg, which) -> {
                    String text = d.editCorrection.getText().toString().trim();
                    if (text.isEmpty()) return;
                    history.correct(entry.getId(), text);
                    Toast.makeText(requireContext(), R.string.correction_saved, Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.show();
        d.editCorrection.requestFocus();
    }
}
