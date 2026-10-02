package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The games started last, newest first, so the home screen can offer them again after the session ends.
 */
public class RecentGames {
    private static final String PREFS = "apollo_recent_games";
    private static final String KEY_LIST = "list";
    private static final int MAX = 8;

    public static class Game {
        public final String computerUuid;
        public final int appId;
        public final String name;

        Game(String computerUuid, int appId, String name) {
            this.computerUuid = computerUuid;
            this.appId = appId;
            this.name = name;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Game> get(Context context) {
        List<Game> games = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_LIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                games.add(new Game(object.getString("uuid"), object.getInt("appId"), object.optString("name", null)));
            }
        } catch (JSONException ignored) {
        }
        return games;
    }

    /**
     * Moves the game to the front. A null name keeps the one already known.
     */
    public static void add(Context context, String computerUuid, int appId, String name) {
        List<Game> games = get(context);
        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            if (game.computerUuid.equals(computerUuid) && game.appId == appId) {
                if (i == 0 && (name == null || name.equals(game.name))) {
                    return;
                }
                if (name == null) {
                    name = game.name;
                }
                games.remove(i);
                break;
            }
        }
        games.add(0, new Game(computerUuid, appId, name));
        while (games.size() > MAX) {
            games.remove(games.size() - 1);
        }
        save(context, games);
    }

    public static void remove(Context context, String computerUuid, int appId) {
        List<Game> games = get(context);
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).computerUuid.equals(computerUuid) && games.get(i).appId == appId) {
                games.remove(i);
                save(context, games);
                return;
            }
        }
    }

    public static void clearName(Context context, String computerUuid, int appId) {
        List<Game> games = get(context);
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).computerUuid.equals(computerUuid) && games.get(i).appId == appId) {
                games.set(i, new Game(computerUuid, appId, null));
                save(context, games);
                return;
            }
        }
    }

    private static void save(Context context, List<Game> games) {
        JSONArray array = new JSONArray();
        try {
            for (Game game : games) {
                JSONObject object = new JSONObject();
                object.put("uuid", game.computerUuid);
                object.put("appId", game.appId);
                if (game.name != null) {
                    object.put("name", game.name);
                }
                array.put(object);
            }
        } catch (JSONException ignored) {
        }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply();
    }
}
