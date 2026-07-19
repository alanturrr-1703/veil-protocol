package com.veil.api.rest;

import com.veil.api.DTOs.LeaderboardView;
import com.veil.api.session.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, cross-match analytics. Served SEPARATELY from PlayerView because it holds no
 * per-viewer secret: every field (points, wins, win rate) is aggregate history safe to
 * show anyone. Rows arrive pre-ranked, so the frontend renders them top-down as-is.
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final GameService games;

    public LeaderboardController(GameService games) {
        this.games = games;
    }

    @GetMapping
    public LeaderboardView leaderboard() {
        return games.leaderboard();
    }
}
