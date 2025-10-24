package com.example.guerraentrevecinos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GameHistoryAdapter extends RecyclerView.Adapter<GameHistoryAdapter.ViewHolder> {

    private List<GameHistoryItem> games;

    public GameHistoryAdapter(List<GameHistoryItem> games) {
        this.games = games;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GameHistoryItem game = games.get(position);

        // Result badge
        if (game.isWin()) {
            holder.tvGameResult.setText("WIN");
            holder.tvGameResult.setBackgroundResource(R.drawable.win_badge);
        } else {
            holder.tvGameResult.setText("LOSS");
            holder.tvGameResult.setBackgroundResource(R.drawable.loss_badge);
        }

        // Stats
        holder.tvGameAccuracy.setText(String.format("%.0f%%", game.getAccuracy()));
        holder.tvGameUnitsDestroyed.setText(String.valueOf(game.getUnitsDestroyed()));
        holder.tvGameRounds.setText(String.valueOf(game.getRounds()));
        holder.tvGameDate.setText(game.getDate());
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvGameResult, tvGameDate, tvGameAccuracy,
                tvGameUnitsDestroyed, tvGameRounds;

        ViewHolder(View itemView) {
            super(itemView);
            tvGameResult = itemView.findViewById(R.id.tvGameResult);
            tvGameDate = itemView.findViewById(R.id.tvGameDate);
            tvGameAccuracy = itemView.findViewById(R.id.tvGameAccuracy);
            tvGameUnitsDestroyed = itemView.findViewById(R.id.tvGameUnitsDestroyed);
            tvGameRounds = itemView.findViewById(R.id.tvGameRounds);
        }
    }
}