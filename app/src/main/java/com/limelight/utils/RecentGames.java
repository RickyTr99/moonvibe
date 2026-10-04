package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The games started last, so the home screen can offer them again after the session ends.
 * A game keeps its place once in the list: new games come first and push the oldest out.
 * The PC name is kept too, so the list shows before the PCs are loaded.
 */
public class RecentGames {
    private static final String PREFS = "apollo_recent_games";
    private static final String KEY_LIST = "list";
    private static final int MAX = 8;

    public static class Game {
        public final String computerUuid;
        public final int appId;
        public final String name;
        public final String computerName;

        Game(String computerUuid, int appId, String name, String computerName) {
            this.computerUuid = computerUuid;
            this.appId = appId;
            this.name = name;
            this.computerName = computerName;
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
                games.add(new Game(object.getString("uuid"), object.getInt("appId"),
                        object.optString("name", null), object.optString("pc", null)));
            }
        } catch (JSONException ignored) {
        }
        return games;
    }

    /**
     * Adds the game in front, or updates its names where it is. A null name keeps the one already known.
     */
    public static void add(Context context, String computerUuid, int appId, String name, String computerName) {
        List<Game> games = get(context);
        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            if (game.computerUuid.equals(computerUuid) && game.appId == appId) {
                String newName = name != null ? name : game.name;
                String newComputerName = computerName != null ? computerName : game.computerName;
                if (equals(newName, game.name) && equals(newComputerName, game.computerName)) {
                    return;
                }
                games.set(i, new Game(computerUuid, appId, newName, newComputerName));
                save(context, games);
                return;
            }
        }
        games.add(0, new Game(computerUuid, appId, name, computerName));
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
            Game game = games.get(i);
            if (game.computerUuid.equals(computerUuid) && game.appId == appId) {
                games.set(i, new Game(computerUuid, appId, null, game.computerName));
                save(context, games);
                return;
            }
        }
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
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
                if (game.computerName != null) {
                    object.put("pc", game.computerName);
                }
                array.put(object);
            }
        } catch (JSONException ignored) {
        }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply();
    }
}
