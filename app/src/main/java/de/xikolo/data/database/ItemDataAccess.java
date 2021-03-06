package de.xikolo.data.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import de.xikolo.data.entities.Item;
import de.xikolo.data.entities.Module;

public class ItemDataAccess extends DataAccess {

    public ItemDataAccess(DatabaseHelper databaseHelper) {
        super(databaseHelper);
    }

    public void addItem(Module module, Item item) {
        openDatabase().insert(ItemTable.TABLE_NAME, null, buildContentValues(module, item));

        closeDatabase();
    }

    public void addOrUpdateItem(Module module, Item item) {
        if (updateItem(module, item) < 1) {
            addItem(module, item);
        }
    }

    public Item getItem(String id) {
        Cursor cursor = openDatabase().query(
                ItemTable.TABLE_NAME,
                new String[]{
                        ItemTable.COLUMN_ID,
                        ItemTable.COLUMN_POSITION,
                        ItemTable.COLUMN_TITLE,
                        ItemTable.COLUMN_TYPE,
                        ItemTable.COLUMN_AVAILABLE_FROM,
                        ItemTable.COLUMN_AVAILABLE_TO,
                        ItemTable.COLUMN_EXERCISE_TYPE,
                        ItemTable.COLUMN_LOCKED,
                        ItemTable.COLUMN_VISITED,
                        ItemTable.COLUMN_COMPLETED,
                },
                ItemTable.COLUMN_ID + " =? ",
                new String[]{String.valueOf(id)}, null, null, null, null);

        Item item = null;
        if (cursor.moveToFirst()) {
            item = buildItem(cursor);
        }
        cursor.close();
        closeDatabase();

        return item;
    }

    public List<Item> getAllItems() {
        List<Item> itemList = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + ItemTable.TABLE_NAME;

        Cursor cursor = openDatabase().rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Item item = buildItem(cursor);
                itemList.add(item);
            } while (cursor.moveToNext());
        }

        cursor.close();
        closeDatabase();

        return itemList;
    }

    public List<Item> getAllItemsForModule(Module module) {
        List<Item> itemList = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + ItemTable.TABLE_NAME + " WHERE " + ItemTable.COLUMN_MODULE_ID + " = \'" + module.id + "\'";

        Cursor cursor = openDatabase().rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Item item = buildItem(cursor);
                itemList.add(item);
            } while (cursor.moveToNext());
        }

        cursor.close();
        closeDatabase();

        return itemList;
    }

    private Item buildItem(Cursor cursor) {
        Item item = new Item();

        item.id = cursor.getString(0);
        item.position = cursor.getInt(1);
        item.title = cursor.getString(2);
        item.type = cursor.getString(3);
        item.available_from = cursor.getString(4);
        item.available_to = cursor.getString(5);
        item.exercise_type = cursor.getString(6);
        item.locked = cursor.getInt(7) != 0;
        item.progress.visited = cursor.getInt(8) != 0;
        item.progress.completed = cursor.getInt(9) != 0;

        return item;
    }

    private ContentValues buildContentValues(Module module, Item item) {
        ContentValues values = new ContentValues();
        values.put(ItemTable.COLUMN_ID, item.id);
        values.put(ItemTable.COLUMN_POSITION, item.position);
        values.put(ItemTable.COLUMN_TITLE, item.title);
        values.put(ItemTable.COLUMN_TYPE, item.type);
        values.put(ItemTable.COLUMN_AVAILABLE_FROM, item.available_from);
        values.put(ItemTable.COLUMN_AVAILABLE_TO, item.available_to);
        values.put(ItemTable.COLUMN_EXERCISE_TYPE, item.exercise_type);
        values.put(ItemTable.COLUMN_LOCKED, item.locked);
        values.put(ItemTable.COLUMN_VISITED, item.progress.visited);
        values.put(ItemTable.COLUMN_COMPLETED, item.progress.completed);
        values.put(ItemTable.COLUMN_MODULE_ID, module.id);

        return values;
    }

    public int getItemsCount() {
        String countQuery = "SELECT * FROM " + ItemTable.TABLE_NAME;
        Cursor cursor = openDatabase().rawQuery(countQuery, null);

        int count = cursor.getCount();

        cursor.close();
        closeDatabase();

        return count;
    }

    public int updateItem(Module module, Item item) {
        int affected = openDatabase().update(
                ItemTable.TABLE_NAME,
                buildContentValues(module, item),
                ItemTable.COLUMN_ID + " =? ",
                new String[]{String.valueOf(item.id)});

        closeDatabase();

        return affected;
    }

    public void deleteItem(Item item) {
        openDatabase().delete(
                ItemTable.TABLE_NAME,
                ItemTable.COLUMN_ID + " =? ",
                new String[]{String.valueOf(item.id)});

        closeDatabase();
    }

}
