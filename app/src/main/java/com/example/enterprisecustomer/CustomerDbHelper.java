package com.example.enterprisecustomer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CustomerDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "enterprise_customer_manager.db";
    public static final int DB_VERSION = 1;

    public static final String STATUS_NONE = "未设置";
    public static final String STATUS_FOCUS = "关注";
    public static final String STATUS_FOLLOW = "跟进";
    public static final String STATUS_IMPORTANT = "重点";

    public CustomerDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups_tbl (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "created_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE companies (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "group_id INTEGER NOT NULL," +
                "seq INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "normalized_name TEXT NOT NULL UNIQUE," +
                "industry TEXT," +
                "employee_count TEXT," +
                "region TEXT," +
                "address TEXT," +
                "customer_status TEXT," +
                "tags TEXT," +
                "extra_info TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE contacts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "company_id INTEGER NOT NULL," +
                "contact_name TEXT," +
                "phone TEXT NOT NULL," +
                "phone_norm TEXT NOT NULL," +
                "contact_order INTEGER NOT NULL," +
                "imported INTEGER NOT NULL DEFAULT 0," +
                "raw_contact_id INTEGER," +
                "imported_at TEXT," +
                "created_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "company_id INTEGER NOT NULL," +
                "content TEXT NOT NULL," +
                "created_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX idx_companies_group_seq ON companies(group_id, seq)");
        db.execSQL("CREATE INDEX idx_companies_status ON companies(customer_status)");
        db.execSQL("CREATE INDEX idx_contacts_company ON contacts(company_id)");
        db.execSQL("CREATE INDEX idx_contacts_phone ON contacts(phone_norm)");
        ContentValues cv = new ContentValues();
        cv.put("name", "默认分组");
        cv.put("created_at", now());
        db.insert("groups_tbl", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notes");
        db.execSQL("DROP TABLE IF EXISTS contacts");
        db.execSQL("DROP TABLE IF EXISTS companies");
        db.execSQL("DROP TABLE IF EXISTS groups_tbl");
        onCreate(db);
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static String normalizeCompany(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\u3000', ' ');
        t = t.replaceAll("\\s+", "");
        t = t.replace('（', '(').replace('）', ')');
        return t;
    }

    public static String normalizePhone(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replaceAll("[^0-9+]", "");
        if (t.startsWith("+86")) t = t.substring(3);
        if (t.startsWith("86") && t.length() == 13) t = t.substring(2);
        return t;
    }

    public static String nonNull(String s) { return s == null ? "" : s; }
    public static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    public long ensureGroup(String name) {
        if (empty(name)) name = "默认分组";
        name = name.trim();
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM groups_tbl WHERE name=?", new String[]{name});
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally { c.close(); }
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("created_at", now());
        return db.insert("groups_tbl", null, cv);
    }

    public List<GroupItem> getGroups() {
        ArrayList<GroupItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name FROM groups_tbl ORDER BY id", null);
        try {
            while (c.moveToNext()) list.add(new GroupItem(c.getLong(0), c.getString(1)));
        } finally { c.close(); }
        return list;
    }

    public String getGroupName(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT name FROM groups_tbl WHERE id=?", new String[]{String.valueOf(id)});
        try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
        return "默认分组";
    }

    public void renameGroup(long id, String name) {
        if (empty(name)) return;
        ContentValues cv = new ContentValues();
        cv.put("name", name.trim());
        getWritableDatabase().update("groups_tbl", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteGroup(long id) {
        long defaultId = ensureGroup("默认分组");
        if (id == defaultId) return;
        ContentValues cv = new ContentValues();
        cv.put("group_id", defaultId);
        getWritableDatabase().update("companies", cv, "group_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("groups_tbl", "id=?", new String[]{String.valueOf(id)});
        resequenceGroup(defaultId);
    }

    private int nextSeq(SQLiteDatabase db, long groupId) {
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(seq),0)+1 FROM companies WHERE group_id=?", new String[]{String.valueOf(groupId)});
        try { return c.moveToFirst() ? c.getInt(0) : 1; } finally { c.close(); }
    }

    public void resequenceGroup(long groupId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM companies WHERE group_id=? ORDER BY seq ASC,id ASC", new String[]{String.valueOf(groupId)});
        int seq = 1;
        db.beginTransaction();
        try {
            while (c.moveToNext()) {
                ContentValues cv = new ContentValues();
                cv.put("seq", seq++);
                db.update("companies", cv, "id=?", new String[]{String.valueOf(c.getLong(0))});
            }
            db.setTransactionSuccessful();
        } finally { c.close(); db.endTransaction(); }
    }

    public ImportResult importRows(List<Map<String, String>> rows, long fallbackGroupId, boolean preferExcelGroup) {
        ImportResult result = new ImportResult();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map<String, String> row : rows) {
                String companyName = first(row, "公司名称", "企业名称", "单位名称", "客户名称", "公司", "企业", "名称");
                if (empty(companyName)) { result.skippedNoCompany++; continue; }
                String groupName = preferExcelGroup ? first(row, "分组", "客户分组", "组别") : "";
                long groupId = empty(groupName) ? fallbackGroupId : ensureGroup(groupName);
                long companyId = findCompanyId(db, companyName);
                boolean isNew = companyId <= 0;
                if (isNew) {
                    companyId = insertCompany(db, row, companyName, groupId);
                    result.newCompanies++;
                } else {
                    supplementCompany(db, companyId, row);
                    result.mergedCompanies++;
                }
                int contacts = importContactsForCompany(db, companyId, row);
                int notes = importNotesForCompany(db, companyId, row);
                int tags = importTagsAndExtraForCompany(db, companyId, row);
                result.newContacts += contacts;
                result.newNotes += notes;
                result.newTags += tags;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return result;
    }

    private long findCompanyId(SQLiteDatabase db, String companyName) {
        String norm = normalizeCompany(companyName);
        Cursor c = db.rawQuery("SELECT id FROM companies WHERE normalized_name=?", new String[]{norm});
        try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
    }

    private long insertCompany(SQLiteDatabase db, Map<String, String> row, String name, long groupId) {
        ContentValues cv = new ContentValues();
        cv.put("group_id", groupId);
        cv.put("seq", nextSeq(db, groupId));
        cv.put("name", name.trim());
        cv.put("normalized_name", normalizeCompany(name));
        cv.put("industry", first(row, "所属行业", "行业", "经营行业"));
        cv.put("employee_count", first(row, "参保人数", "人数", "员工人数"));
        cv.put("region", first(row, "区域", "地区", "所在区域"));
        cv.put("address", first(row, "地址", "企业地址", "注册地址", "经营地址"));
        cv.put("customer_status", ""); // 状态必须手动设置，Excel 中的客户状态不导入
        cv.put("tags", collectTagValues(row));
        cv.put("extra_info", collectExtraValues(row));
        cv.put("created_at", now());
        cv.put("updated_at", now());
        return db.insert("companies", null, cv);
    }

    private void supplementCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        CompanyItem old = getCompany(companyId);
        ContentValues cv = new ContentValues();
        putIfOldEmpty(cv, "industry", old.industry, first(row, "所属行业", "行业", "经营行业"));
        putIfOldEmpty(cv, "employee_count", old.employeeCount, first(row, "参保人数", "人数", "员工人数"));
        putIfOldEmpty(cv, "region", old.region, first(row, "区域", "地区", "所在区域"));
        putIfOldEmpty(cv, "address", old.address, first(row, "地址", "企业地址", "注册地址", "经营地址"));
        String newTags = mergeSemi(old.tags, collectTagValues(row));
        String newExtra = mergeSemi(old.extraInfo, collectExtraValues(row));
        cv.put("tags", newTags);
        cv.put("extra_info", newExtra);
        cv.put("updated_at", now());
        db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
    }

    private void putIfOldEmpty(ContentValues cv, String col, String oldVal, String newVal) {
        if (empty(oldVal) && !empty(newVal)) cv.put(col, newVal.trim());
    }

    private int importContactsForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        int count = 0;
        for (int i = 1; i <= 80; i++) {
            String name = first(row, "联系人" + i, "联系人姓名" + i, "联系人" + i + "姓名", "姓名" + i);
            String phone = first(row, "电话号码" + i, "手机号" + i, "联系电话" + i, "电话" + i, "手机" + i);
            if (!empty(phone) || !empty(name)) {
                if (empty(phone)) continue;
                if (addContactIfNew(db, companyId, name, phone)) count++;
            }
        }
        String name = first(row, "联系人", "联系人姓名", "姓名");
        String phone = first(row, "电话号码", "手机号", "联系电话", "电话", "手机", "移动电话", "联系方式");
        if (!empty(phone)) {
            if (addContactIfNew(db, companyId, name, phone)) count++;
        }
        return count;
    }

    private boolean addContactIfNew(SQLiteDatabase db, long companyId, String name, String phone) {
        String norm = normalizePhone(phone);
        if (empty(norm)) return false;
        Cursor c = db.rawQuery("SELECT id FROM contacts WHERE company_id=? AND phone_norm=?", new String[]{String.valueOf(companyId), norm});
        try { if (c.moveToFirst()) return false; } finally { c.close(); }
        int order = nextContactOrder(db, companyId);
        ContentValues cv = new ContentValues();
        cv.put("company_id", companyId);
        cv.put("contact_name", nonNull(name).trim());
        cv.put("phone", phone.trim());
        cv.put("phone_norm", norm);
        cv.put("contact_order", order);
        cv.put("imported", 0);
        cv.put("created_at", now());
        db.insert("contacts", null, cv);
        return true;
    }

    private int nextContactOrder(SQLiteDatabase db, long companyId) {
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(contact_order),0)+1 FROM contacts WHERE company_id=?", new String[]{String.valueOf(companyId)});
        try { return c.moveToFirst() ? c.getInt(0) : 1; } finally { c.close(); }
    }

    private int importNotesForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        int count = 0;
        for (int i = 1; i <= 100; i++) {
            String note = first(row, "备注" + i, "备注信息" + i, "跟进记录" + i, "记录" + i);
            if (!empty(note) && addNoteIfNew(db, companyId, note)) count++;
        }
        String note = first(row, "备注", "备注信息", "跟进记录", "记录", "说明");
        if (!empty(note) && addNoteIfNew(db, companyId, note)) count++;
        return count;
    }

    public boolean addNoteIfNew(SQLiteDatabase db, long companyId, String content) {
        if (empty(content)) return false;
        String val = content.trim();
        Cursor c = db.rawQuery("SELECT id FROM notes WHERE company_id=? AND content=?", new String[]{String.valueOf(companyId), val});
        try { if (c.moveToFirst()) return false; } finally { c.close(); }
        ContentValues cv = new ContentValues();
        cv.put("company_id", companyId);
        cv.put("content", val);
        cv.put("created_at", now());
        db.insert("notes", null, cv);
        return true;
    }

    private int importTagsAndExtraForCompany(SQLiteDatabase db, long companyId, Map<String, String> row) {
        CompanyItem old = getCompany(companyId);
        String newTags = mergeSemi(old.tags, collectTagValues(row));
        String newExtra = mergeSemi(old.extraInfo, collectExtraValues(row));
        if (!newTags.equals(nonNull(old.tags)) || !newExtra.equals(nonNull(old.extraInfo))) {
            ContentValues cv = new ContentValues();
            cv.put("tags", newTags);
            cv.put("extra_info", newExtra);
            cv.put("updated_at", now());
            db.update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
            return 1;
        }
        return 0;
    }

    private static String mergeSemi(String oldVal, String addVal) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : nonNull(oldVal).split("；|;")) if (!empty(part)) set.add(part.trim());
        for (String part : nonNull(addVal).split("；|;")) if (!empty(part)) set.add(part.trim());
        StringBuilder sb = new StringBuilder();
        for (String s : set) { if (sb.length() > 0) sb.append("；"); sb.append(s); }
        return sb.toString();
    }

    private String collectTagValues(Map<String, String> row) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            String k = cleanHeader(key);
            String v = row.get(key);
            if (empty(v)) continue;
            if (k.startsWith("标签") || k.equals("客户标签")) tags.add(v.trim());
        }
        return join(tags);
    }

    private String collectExtraValues(Map<String, String> row) {
        LinkedHashSet<String> extra = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            String k = cleanHeader(key);
            String v = row.get(key);
            if (empty(v)) continue;
            if (isStandardHeader(k)) continue;
            if (k.equals("客户状态")) continue; // 客户状态不从 Excel 导入
            extra.add(k + ":" + v.trim());
        }
        return join(extra);
    }

    private static String join(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String s : values) { if (sb.length() > 0) sb.append("；"); sb.append(s); }
        return sb.toString();
    }

    public static boolean isStandardHeader(String k) {
        String h = cleanHeader(k);
        if (h.matches("联系人\\d*|联系人姓名\\d*|联系人\\d+姓名|姓名\\d*")) return true;
        if (h.matches("电话号码\\d*|手机号\\d*|联系电话\\d*|电话\\d*|手机\\d*|移动电话|联系方式")) return true;
        if (h.matches("备注\\d*|备注信息\\d*|跟进记录\\d*|记录\\d*|说明")) return true;
        if (h.matches("标签\\d*|客户标签")) return true;
        return h.equals("序号") || h.equals("分组") || h.equals("客户分组") || h.equals("组别") ||
                h.equals("公司名称") || h.equals("企业名称") || h.equals("单位名称") || h.equals("客户名称") || h.equals("公司") || h.equals("企业") || h.equals("名称") ||
                h.equals("所属行业") || h.equals("行业") || h.equals("经营行业") ||
                h.equals("参保人数") || h.equals("人数") || h.equals("员工人数") ||
                h.equals("区域") || h.equals("地区") || h.equals("所在区域") ||
                h.equals("地址") || h.equals("企业地址") || h.equals("注册地址") || h.equals("经营地址");
    }

    public static String cleanHeader(String s) {
        if (s == null) return "";
        return s.trim().replace(" ", "").replace("　", "");
    }

    public static String first(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            String target = cleanHeader(alias);
            for (String key : row.keySet()) {
                if (cleanHeader(key).equals(target)) {
                    String v = row.get(key);
                    if (!empty(v)) return v.trim();
                }
            }
        }
        return "";
    }

    public Stats getStats() {
        Stats s = new Stats();
        SQLiteDatabase db = getReadableDatabase();
        s.companyCount = intQuery(db, "SELECT COUNT(*) FROM companies");
        s.contactCount = intQuery(db, "SELECT COUNT(*) FROM contacts");
        s.importedCount = intQuery(db, "SELECT COUNT(*) FROM contacts WHERE imported=1");
        s.statusNone = intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status IS NULL OR customer_status='' ");
        s.focusCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status='关注'");
        s.followCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status='跟进'");
        s.importantCount = intQuery(db, "SELECT COUNT(*) FROM companies WHERE customer_status='重点'");
        return s;
    }

    private int intQuery(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public List<CompanyItem> getCompanies(String query, String status, long groupId) {
        ArrayList<CompanyItem> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        ArrayList<String> args = new ArrayList<>();
        sql.append("SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at,");
        sql.append("(SELECT COUNT(*) FROM contacts ct WHERE ct.company_id=c.id),");
        sql.append("(SELECT COUNT(*) FROM notes n WHERE n.company_id=c.id),");
        sql.append("(SELECT COUNT(*) FROM contacts ct WHERE ct.company_id=c.id AND ct.imported=1) ");
        sql.append("FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE 1=1 ");
        if (groupId > 0) { sql.append("AND c.group_id=? "); args.add(String.valueOf(groupId)); }
        if (!empty(status)) {
            if (STATUS_NONE.equals(status)) sql.append("AND (c.customer_status IS NULL OR c.customer_status='') ");
            else { sql.append("AND c.customer_status=? "); args.add(status); }
        }
        if (!empty(query)) {
            String like = "%" + query.trim() + "%";
            sql.append("AND (c.name LIKE ? OR c.industry LIKE ? OR c.region LIKE ? OR c.address LIKE ? OR c.tags LIKE ? OR c.extra_info LIKE ? ");
            for (int i=0;i<6;i++) args.add(like);
            sql.append("OR EXISTS(SELECT 1 FROM contacts ct WHERE ct.company_id=c.id AND (ct.contact_name LIKE ? OR ct.phone LIKE ?)) ");
            args.add(like); args.add(like);
            sql.append("OR EXISTS(SELECT 1 FROM notes n WHERE n.company_id=c.id AND n.content LIKE ?)) ");
            args.add(like);
        }
        sql.append("ORDER BY g.id ASC,c.seq ASC,c.id ASC");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) list.add(companyFromCursor(c));
        } finally { c.close(); }
        return list;
    }

    private CompanyItem companyFromCursor(Cursor c) {
        CompanyItem it = new CompanyItem();
        int i=0;
        it.id = c.getLong(i++);
        it.groupId = c.getLong(i++);
        it.groupName = c.getString(i++);
        it.seq = c.getInt(i++);
        it.name = c.getString(i++);
        it.industry = c.getString(i++);
        it.employeeCount = c.getString(i++);
        it.region = c.getString(i++);
        it.address = c.getString(i++);
        it.customerStatus = c.getString(i++);
        it.tags = c.getString(i++);
        it.extraInfo = c.getString(i++);
        it.createdAt = c.getString(i++);
        it.updatedAt = c.getString(i++);
        it.contactCount = c.getInt(i++);
        it.noteCount = c.getInt(i++);
        it.importedContactCount = c.getInt(i++);
        return it;
    }

    public CompanyItem getCompany(long id) {
        String sql = "SELECT c.id,c.group_id,g.name,c.seq,c.name,c.industry,c.employee_count,c.region,c.address,c.customer_status,c.tags,c.extra_info,c.created_at,c.updated_at," +
                "(SELECT COUNT(*) FROM contacts ct WHERE ct.company_id=c.id)," +
                "(SELECT COUNT(*) FROM notes n WHERE n.company_id=c.id)," +
                "(SELECT COUNT(*) FROM contacts ct WHERE ct.company_id=c.id AND ct.imported=1) " +
                "FROM companies c LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE c.id=?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? companyFromCursor(c) : new CompanyItem(); } finally { c.close(); }
    }

    public long addCompany(String groupName, String name, String industry, String employeeCount, String region, String address) {
        if (empty(name)) return -1;
        SQLiteDatabase db = getWritableDatabase();
        long existed = findCompanyId(db, name);
        if (existed > 0) return existed;
        long groupId = ensureGroup(groupName);
        ContentValues cv = new ContentValues();
        cv.put("group_id", groupId); cv.put("seq", nextSeq(db, groupId));
        cv.put("name", name.trim()); cv.put("normalized_name", normalizeCompany(name));
        cv.put("industry", nonNull(industry)); cv.put("employee_count", nonNull(employeeCount));
        cv.put("region", nonNull(region)); cv.put("address", nonNull(address));
        cv.put("customer_status", ""); cv.put("tags", ""); cv.put("extra_info", "");
        cv.put("created_at", now()); cv.put("updated_at", now());
        return db.insert("companies", null, cv);
    }

    public void updateCompanyBasic(long id, String groupName, String name, String industry, String employeeCount, String region, String address) {
        long groupId = ensureGroup(groupName);
        CompanyItem old = getCompany(id);
        ContentValues cv = new ContentValues();
        cv.put("group_id", groupId); cv.put("name", name.trim()); cv.put("normalized_name", normalizeCompany(name));
        cv.put("industry", nonNull(industry)); cv.put("employee_count", nonNull(employeeCount));
        cv.put("region", nonNull(region)); cv.put("address", nonNull(address));
        cv.put("updated_at", now());
        getWritableDatabase().update("companies", cv, "id=?", new String[]{String.valueOf(id)});
        if (old.groupId != groupId) { resequenceGroup(old.groupId); resequenceGroup(groupId); }
    }

    public void setCompanyStatus(long id, String status) {
        if (STATUS_NONE.equals(status)) status = "";
        ContentValues cv = new ContentValues();
        cv.put("customer_status", nonNull(status));
        cv.put("updated_at", now());
        getWritableDatabase().update("companies", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteCompany(long id) {
        SQLiteDatabase db = getWritableDatabase();
        CompanyItem old = getCompany(id);
        db.delete("notes", "company_id=?", new String[]{String.valueOf(id)});
        db.delete("contacts", "company_id=?", new String[]{String.valueOf(id)});
        db.delete("companies", "id=?", new String[]{String.valueOf(id)});
        resequenceGroup(old.groupId);
    }

    public long addContact(long companyId, String name, String phone) {
        SQLiteDatabase db = getWritableDatabase();
        if (addContactIfNew(db, companyId, name, phone)) {
            Cursor c = db.rawQuery("SELECT last_insert_rowid()", null);
            try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
        }
        return -1;
    }

    public void deleteContact(long id) {
        getWritableDatabase().delete("contacts", "id=?", new String[]{String.valueOf(id)});
    }

    public void addNote(long companyId, String content) {
        addNoteIfNew(getWritableDatabase(), companyId, content);
    }

    public void deleteNote(long id) {
        getWritableDatabase().delete("notes", "id=?", new String[]{String.valueOf(id)});
    }

    public void updateTags(long companyId, String tags) {
        ContentValues cv = new ContentValues();
        cv.put("tags", nonNull(tags));
        cv.put("updated_at", now());
        getWritableDatabase().update("companies", cv, "id=?", new String[]{String.valueOf(companyId)});
    }

    public List<ContactItem> getContacts(long companyId, Boolean importedOnly) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE 1=1 ";
        ArrayList<String> args = new ArrayList<>();
        if (companyId > 0) { sql += "AND ct.company_id=? "; args.add(String.valueOf(companyId)); }
        if (importedOnly != null) sql += importedOnly ? "AND ct.imported=1 " : "AND ct.imported=0 ";
        sql += "ORDER BY g.id,c.seq,ct.contact_order";
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try {
            while (c.moveToNext()) list.add(contactFromCursor(c));
        } finally { c.close(); }
        return list;
    }

    public List<ContactItem> getContactsForExport(long companyId) { return getContacts(companyId, null); }

    private ContactItem contactFromCursor(Cursor c) {
        ContactItem it = new ContactItem();
        int i=0;
        it.id = c.getLong(i++); it.companyId = c.getLong(i++); it.companyName = c.getString(i++);
        it.contactName = c.getString(i++); it.phone = c.getString(i++); it.phoneNorm = c.getString(i++);
        it.order = c.getInt(i++); it.imported = c.getInt(i++) == 1;
        it.rawContactId = c.isNull(i) ? 0 : c.getLong(i); i++;
        it.importedAt = c.getString(i++); it.groupName = c.getString(i++); it.customerStatus = c.getString(i++);
        it.companySeq = c.getInt(i++); it.groupId = c.getLong(i++);
        return it;
    }

    public List<NoteItem> getNotes(long companyId) {
        ArrayList<NoteItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,company_id,content,created_at FROM notes WHERE company_id=? ORDER BY id", new String[]{String.valueOf(companyId)});
        try {
            while (c.moveToNext()) {
                NoteItem n = new NoteItem();
                n.id=c.getLong(0); n.companyId=c.getLong(1); n.content=c.getString(2); n.createdAt=c.getString(3);
                list.add(n);
            }
        } finally { c.close(); }
        return list;
    }

    public void markContactImported(long contactId, long rawContactId) {
        ContentValues cv = new ContentValues();
        cv.put("imported", 1);
        cv.put("raw_contact_id", rawContactId);
        cv.put("imported_at", now());
        getWritableDatabase().update("contacts", cv, "id=?", new String[]{String.valueOf(contactId)});
    }

    public void markContactUnimported(long contactId) {
        ContentValues cv = new ContentValues();
        cv.put("imported", 0);
        cv.putNull("raw_contact_id");
        cv.putNull("imported_at");
        getWritableDatabase().update("contacts", cv, "id=?", new String[]{String.valueOf(contactId)});
    }

    public List<ContactItem> getUnimportedContactsByRange(long groupId, int startSeq, int endSeq) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id " +
                "WHERE ct.imported=0 AND c.group_id=? AND c.seq>=? AND c.seq<=? ORDER BY c.seq,ct.contact_order";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(groupId), String.valueOf(startSeq), String.valueOf(endSeq)});
        try { while (c.moveToNext()) list.add(contactFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<ContactItem> getUnimportedContactsByStatus(String status) {
        ArrayList<ContactItem> list = new ArrayList<>();
        String sql = "SELECT ct.id,ct.company_id,c.name,ct.contact_name,ct.phone,ct.phone_norm,ct.contact_order,ct.imported,ct.raw_contact_id,ct.imported_at,g.name,c.customer_status,c.seq,c.group_id " +
                "FROM contacts ct JOIN companies c ON ct.company_id=c.id LEFT JOIN groups_tbl g ON c.group_id=g.id WHERE ct.imported=0 ";
        ArrayList<String> args = new ArrayList<>();
        if (!empty(status)) { sql += "AND c.customer_status=? "; args.add(status); }
        sql += "ORDER BY g.id,c.seq,ct.contact_order";
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try { while (c.moveToNext()) list.add(contactFromCursor(c)); } finally { c.close(); }
        return list;
    }

    public List<CompanyItem> getCompaniesForExport(String mode, long groupId, int startSeq, int endSeq) {
        String status = null;
        if (STATUS_NONE.equals(mode) || STATUS_FOCUS.equals(mode) || STATUS_FOLLOW.equals(mode) || STATUS_IMPORTANT.equals(mode)) status = mode;
        return getCompanies("", status, groupId);
    }

    public Map<Long, List<ContactItem>> contactsByCompany(List<CompanyItem> companies) {
        LinkedHashMap<Long, List<ContactItem>> map = new LinkedHashMap<>();
        for (CompanyItem c : companies) map.put(c.id, getContactsForExport(c.id));
        return map;
    }

    public Map<Long, List<NoteItem>> notesByCompany(List<CompanyItem> companies) {
        LinkedHashMap<Long, List<NoteItem>> map = new LinkedHashMap<>();
        for (CompanyItem c : companies) map.put(c.id, getNotes(c.id));
        return map;
    }

    public static class GroupItem { public long id; public String name; public GroupItem(long i,String n){id=i;name=n;} public String toString(){return name;} }
    public static class Stats { public int companyCount, contactCount, importedCount, statusNone, focusCount, followCount, importantCount; }
    public static class ImportResult { public int newCompanies, mergedCompanies, newContacts, newNotes, newTags, skippedNoCompany; }

    public static class CompanyItem {
        public long id, groupId; public int seq, contactCount, noteCount, importedContactCount;
        public String groupName, name, industry, employeeCount, region, address, customerStatus, tags, extraInfo, createdAt, updatedAt;
        public String statusText(){ return empty(customerStatus) ? STATUS_NONE : customerStatus; }
        public String importText(){ if (contactCount == 0) return "无联系人"; if (importedContactCount == 0) return "未导入"; if (importedContactCount >= contactCount) return "已导入"; return "部分导入"; }
    }

    public static class ContactItem {
        public long id, companyId, rawContactId, groupId; public int order, companySeq; public boolean imported;
        public String companyName, contactName, phone, phoneNorm, importedAt, groupName, customerStatus;
        public String displayName(){
            String cn = nonNull(contactName).trim();
            if (!empty(cn)) return nonNull(companyName) + "-" + cn;
            if (order > 1) return nonNull(companyName) + "-联系人" + order;
            return nonNull(companyName);
        }
    }

    public static class NoteItem { public long id, companyId; public String content, createdAt; }
}
