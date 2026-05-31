package com.example.enterprisecustomer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_XLSX = 1001;
    private static final int REQ_EXPORT_XLSX = 1002;
    private static final int REQ_CONTACT_PERM = 2001;

    private CustomerDbHelper db;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int currentTab = 0;
    private String currentSearch = "";
    private String currentStatus = "";
    private long currentGroupId = 0;
    private List<String> pendingExportHeaders;
    private List<List<String>> pendingExportRows;
    private List<CustomerDbHelper.ContactItem> pendingImportContacts;

    private final String[] tabs = {"首页", "客户", "通讯录", "导入导出", "我的"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new CustomerDbHelper(this);
        buildRoot();
        render();
        handleIncomingFile(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingFile(intent);
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 251));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
    }

    private void render() {
        content.removeAllViews();
        nav.removeAllViews();
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(tabs[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(i == currentTab ? Color.WHITE : Color.rgb(38, 64, 105));
            t.setBackground(round(i == currentTab ? Color.rgb(30, 94, 255) : Color.TRANSPARENT, dp(14), 0));
            t.setOnClickListener(v -> { currentTab = idx; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(t, lp);
        }
        if (currentTab == 0) renderHome();
        else if (currentTab == 1) renderCustomers();
        else if (currentTab == 2) renderContactSync();
        else if (currentTab == 3) renderImportExport();
        else renderSettings();
    }

    private void renderHome() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(16));
        scroll.addView(box);
        box.addView(header("企业客户管家", "企业客户管理、Excel导入导出与手机通讯录同步"));
        CustomerDbHelper.Stats s = db.getStats();
        LinearLayout grid1 = horizontal(0);
        box.addView(grid1);
        grid1.addView(statCard("总企业", s.companyCount + "", "家公司"), weightLp());
        grid1.addView(statCard("总联系人", s.contactCount + "", "个电话"), weightLp());
        LinearLayout grid2 = horizontal(0);
        box.addView(grid2);
        grid2.addView(statCard("未设置", s.statusNone + "", "待判断"), weightLp());
        grid2.addView(statCard("已导入", s.importedCount + "", "通讯录"), weightLp());
        LinearLayout grid3 = horizontal(0);
        box.addView(grid3);
        grid3.addView(statCard("关注", s.focusCount + "", "客户"), weightLp());
        grid3.addView(statCard("跟进", s.followCount + "", "客户"), weightLp());
        grid3.addView(statCard("重点", s.importantCount + "", "客户"), weightLp());

        box.addView(sectionTitle("快捷操作"));
        LinearLayout ops1 = horizontal(0);
        box.addView(ops1);
        ops1.addView(primaryButton("导入 Excel", v -> openImportFile()), weightLp());
        ops1.addView(primaryButton("新增企业", v -> showCompanyEditor(0)), weightLp());
        LinearLayout ops2 = horizontal(0);
        box.addView(ops2);
        ops2.addView(primaryButton("批量导入通讯录", v -> showBulkImportDialog()), weightLp());
        ops2.addView(primaryButton("导出 Excel", v -> showExportDialog()), weightLp());

        box.addView(sectionTitle("客户状态入口"));
        LinearLayout status = horizontal(0);
        box.addView(status);
        status.addView(statusButton("未设置", CustomerDbHelper.STATUS_NONE), weightLp());
        status.addView(statusButton("关注", CustomerDbHelper.STATUS_FOCUS), weightLp());
        status.addView(statusButton("跟进", CustomerDbHelper.STATUS_FOLLOW), weightLp());
        status.addView(statusButton("重点", CustomerDbHelper.STATUS_IMPORTANT), weightLp());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private Button statusButton(String label, String status) {
        Button b = smallButton(label, v -> { currentTab = 1; currentStatus = status; render(); });
        return b;
    }

    private void renderCustomers() {
        LinearLayout wrapper = vertical(0);
        wrapper.setPadding(dp(12), dp(12), dp(12), 0);
        wrapper.addView(titleRow("客户", "企业为核心，支持模糊搜索与状态筛选"));
        EditText search = new EditText(this);
        search.setHint("输入公司、联系人、电话、区域、标签、备注搜索");
        search.setSingleLine(true);
        search.setText(currentSearch);
        search.setTextSize(14);
        search.setBackground(round(Color.WHITE, dp(12), Color.rgb(220, 226, 236)));
        search.setPadding(dp(12), 0, dp(12), 0);
        wrapper.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));
        search.setOnEditorActionListener((v, actionId, event) -> { currentSearch = search.getText().toString(); renderCustomersOnly(); return false; });
        LinearLayout searchOps = horizontal(0);
        wrapper.addView(searchOps);
        searchOps.addView(smallButton("搜索", v -> { currentSearch = search.getText().toString(); render(); }), weightLp());
        searchOps.addView(smallButton("清空", v -> { currentSearch = ""; currentStatus = ""; currentGroupId = 0; render(); }), weightLp());
        searchOps.addView(smallButton("新增企业", v -> showCompanyEditor(0)), weightLp());
        wrapper.addView(chipBar());
        ScrollView listScroll = scroll();
        LinearLayout list = vertical(dp(8));
        listScroll.addView(list);
        List<CustomerDbHelper.CompanyItem> companies = db.getCompanies(currentSearch, currentStatus, currentGroupId);
        if (companies.isEmpty()) list.addView(emptyBox("暂无客户数据", "可先导入 Excel 或手动新增企业"));
        for (CustomerDbHelper.CompanyItem c : companies) list.addView(companyCard(c));
        wrapper.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(wrapper, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderCustomersOnly() { render(); }

    private View chipBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout bar = horizontal(0);
        hsv.addView(bar);
        bar.addView(chip("全部", currentStatus.equals(""), v -> { currentStatus = ""; render(); }));
        bar.addView(chip("未设置", CustomerDbHelper.STATUS_NONE.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_NONE; render(); }));
        bar.addView(chip("关注", CustomerDbHelper.STATUS_FOCUS.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_FOCUS; render(); }));
        bar.addView(chip("跟进", CustomerDbHelper.STATUS_FOLLOW.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_FOLLOW; render(); }));
        bar.addView(chip("重点", CustomerDbHelper.STATUS_IMPORTANT.equals(currentStatus), v -> { currentStatus = CustomerDbHelper.STATUS_IMPORTANT; render(); }));
        bar.addView(chip("全部分组", currentGroupId == 0, v -> { currentGroupId = 0; render(); }));
        for (CustomerDbHelper.GroupItem g : db.getGroups()) {
            bar.addView(chip(g.name, currentGroupId == g.id, v -> { currentGroupId = g.id; render(); }));
        }
        hsv.setPadding(0, dp(8), 0, dp(8));
        return hsv;
    }

    private View companyCard(CustomerDbHelper.CompanyItem c) {
        LinearLayout card = card();
        LinearLayout top = horizontal(0);
        TextView title = text(String.format(Locale.CHINA, "%03d  %s", c.seq, c.name), 16, Color.rgb(22, 38, 64), true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(label(c.statusText()));
        card.addView(top);
        card.addView(text(c.groupName + "｜" + nvl(c.region, "未填区域") + "｜" + nvl(c.industry, "未填行业"), 13, Color.rgb(90, 103, 125), false));
        card.addView(text("联系人 " + c.contactCount + " ｜备注 " + c.noteCount + " ｜通讯录 " + c.importText(), 13, Color.rgb(90, 103, 125), false));
        if (!CustomerDbHelper.empty(c.tags)) card.addView(text("标签：" + c.tags, 13, Color.rgb(90, 103, 125), false));
        LinearLayout ops = horizontal(0);
        card.addView(ops);
        ops.addView(smallButton("详情", v -> showCompanyDetail(c.id)), weightLp());
        ops.addView(smallButton("导入", v -> importCompanyContacts(c.id)), weightLp());
        ops.addView(smallButton("编辑", v -> showCompanyEditor(c.id)), weightLp());
        return card;
    }

    private void showCompanyDetail(long companyId) {
        CustomerDbHelper.CompanyItem c = db.getCompany(companyId);
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(10));
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(box);
        box.addView(text(c.name, 20, Color.rgb(22, 38, 64), true));
        box.addView(text("分组：" + c.groupName + "    序号：" + c.seq, 14, Color.rgb(90, 103, 125), false));
        box.addView(text("状态：" + c.statusText() + "    通讯录：" + c.importText(), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("行业：" + nvl(c.industry, "未填写"), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("参保人数：" + nvl(c.employeeCount, "未填写"), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("区域：" + nvl(c.region, "未填写"), 14, Color.rgb(90, 103, 125), false));
        box.addView(text("地址：" + nvl(c.address, "未填写"), 14, Color.rgb(90, 103, 125), false));
        if (!CustomerDbHelper.empty(c.tags)) box.addView(text("标签：" + c.tags, 14, Color.rgb(90, 103, 125), false));
        if (!CustomerDbHelper.empty(c.extraInfo)) box.addView(text("扩展信息：" + c.extraInfo, 14, Color.rgb(90, 103, 125), false));
        box.addView(sectionTitle("客户状态"));
        LinearLayout stat = horizontal(0);
        box.addView(stat);
        stat.addView(smallButton("未设置", v -> { db.setCompanyStatus(c.id, ""); showCompanyDetail(c.id); }), weightLp());
        stat.addView(smallButton("关注", v -> { db.setCompanyStatus(c.id, CustomerDbHelper.STATUS_FOCUS); showCompanyDetail(c.id); }), weightLp());
        stat.addView(smallButton("跟进", v -> { db.setCompanyStatus(c.id, CustomerDbHelper.STATUS_FOLLOW); showCompanyDetail(c.id); }), weightLp());
        stat.addView(smallButton("重点", v -> { db.setCompanyStatus(c.id, CustomerDbHelper.STATUS_IMPORTANT); showCompanyDetail(c.id); }), weightLp());
        box.addView(sectionTitle("联系人"));
        List<CustomerDbHelper.ContactItem> contacts = db.getContacts(c.id, null);
        if (contacts.isEmpty()) box.addView(text("暂无联系人", 14, Color.GRAY, false));
        for (CustomerDbHelper.ContactItem ct : contacts) {
            LinearLayout item = vertical(dp(3));
            item.setPadding(dp(8), dp(6), dp(8), dp(6));
            item.setBackground(round(Color.rgb(248, 250, 253), dp(10), Color.rgb(225, 230, 238)));
            item.addView(text((CustomerDbHelper.empty(ct.contactName) ? "联系人" + ct.order : ct.contactName) + "  " + ct.phone + "  " + (ct.imported ? "已导入" : "未导入"), 14, Color.rgb(42, 58, 86), true));
            LinearLayout co = horizontal(0); item.addView(co);
            co.addView(smallButton("拨号", v -> dial(ct.phone)), weightLp());
            co.addView(smallButton("导入", v -> importContactsWithPreview(Arrays.asList(ct))), weightLp());
            co.addView(smallButton("删除", v -> confirm("删除联系人", "只删除App内联系人，不会删除手机通讯录。", () -> { db.deleteContact(ct.id); showCompanyDetail(c.id); })), weightLp());
            box.addView(item);
        }
        box.addView(smallButton("新增联系人", v -> showAddContactDialog(c.id)));
        box.addView(sectionTitle("备注记录"));
        List<CustomerDbHelper.NoteItem> notes = db.getNotes(c.id);
        if (notes.isEmpty()) box.addView(text("暂无备注", 14, Color.GRAY, false));
        for (CustomerDbHelper.NoteItem n : notes) {
            TextView nt = text(n.createdAt + "\n" + n.content, 14, Color.rgb(42, 58, 86), false);
            nt.setPadding(dp(8), dp(6), dp(8), dp(6));
            nt.setBackground(round(Color.rgb(248, 250, 253), dp(10), Color.rgb(225, 230, 238)));
            nt.setOnLongClickListener(v -> { confirm("删除备注", "确认删除这条备注？", () -> { db.deleteNote(n.id); showCompanyDetail(c.id); }); return true; });
            box.addView(nt);
        }
        box.addView(smallButton("新增备注", v -> showAddNoteDialog(c.id)));
        box.addView(sectionTitle("快捷操作"));
        LinearLayout ops1 = horizontal(0); box.addView(ops1);
        ops1.addView(primaryButton("导入该企业全部联系人", v -> importCompanyContacts(c.id)), weightLp());
        ops1.addView(primaryButton("编辑企业", v -> showCompanyEditor(c.id)), weightLp());
        box.addView(smallButton("编辑标签", v -> showEditTags(c.id)));
        box.addView(smallButton("删除企业", v -> confirm("删除企业", "默认只删除App内企业资料，不会删除手机通讯录。", () -> { db.deleteCompany(c.id); Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show(); render(); })));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll).setPositiveButton("关闭", null).create();
        dialog.show();
    }

    private void showCompanyEditor(long companyId) {
        CustomerDbHelper.CompanyItem old = companyId > 0 ? db.getCompany(companyId) : null;
        LinearLayout box = vertical(dp(8));
        box.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText group = edit("分组", old == null ? "默认分组" : old.groupName);
        EditText name = edit("公司名称（必填）", old == null ? "" : old.name);
        EditText industry = edit("所属行业", old == null ? "" : old.industry);
        EditText employee = edit("参保人数", old == null ? "" : old.employeeCount);
        EditText region = edit("区域", old == null ? "" : old.region);
        EditText address = edit("地址", old == null ? "" : old.address);
        box.addView(group); box.addView(name); box.addView(industry); box.addView(employee); box.addView(region); box.addView(address);
        new AlertDialog.Builder(this)
                .setTitle(companyId > 0 ? "编辑企业" : "新增企业")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d,w) -> {
                    if (CustomerDbHelper.empty(name.getText().toString())) { toast("公司名称不能为空"); return; }
                    if (companyId > 0) db.updateCompanyBasic(companyId, group.getText().toString(), name.getText().toString(), industry.getText().toString(), employee.getText().toString(), region.getText().toString(), address.getText().toString());
                    else db.addCompany(group.getText().toString(), name.getText().toString(), industry.getText().toString(), employee.getText().toString(), region.getText().toString(), address.getText().toString());
                    render();
                }).show();
    }

    private void showAddContactDialog(long companyId) {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText name = edit("联系人姓名，可为空", "");
        EditText phone = edit("电话号码", ""); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        box.addView(name); box.addView(phone);
        new AlertDialog.Builder(this).setTitle("新增联系人").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            if (CustomerDbHelper.empty(phone.getText().toString())) { toast("电话不能为空"); return; }
            db.addContact(companyId, name.getText().toString(), phone.getText().toString());
            showCompanyDetail(companyId);
        }).show();
    }

    private void showAddNoteDialog(long companyId) {
        EditText note = edit("请输入备注内容", ""); note.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("新增备注").setView(note).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            db.addNote(companyId, note.getText().toString()); showCompanyDetail(companyId);
        }).show();
    }

    private void showEditTags(long companyId) {
        CustomerDbHelper.CompanyItem c = db.getCompany(companyId);
        EditText tags = edit("多个标签用；分隔", c.tags);
        new AlertDialog.Builder(this).setTitle("编辑标签").setView(tags).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            db.updateTags(companyId, tags.getText().toString()); showCompanyDetail(companyId);
        }).show();
    }

    private void renderContactSync() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(box);
        box.addView(titleRow("通讯录", "查看本App导入的联系人，并支持同步删除"));
        LinearLayout ops = horizontal(0); box.addView(ops);
        ops.addView(primaryButton("批量导入", v -> showBulkImportDialog()), weightLp());
        ops.addView(primaryButton("刷新", v -> render()), weightLp());
        List<CustomerDbHelper.ContactItem> imported = db.getContacts(0, true);
        List<CustomerDbHelper.ContactItem> unimported = db.getContacts(0, false);
        box.addView(sectionTitle("统计"));
        LinearLayout stats = horizontal(0); box.addView(stats);
        stats.addView(statCard("已导入", imported.size()+"", "联系人"), weightLp());
        stats.addView(statCard("未导入", unimported.size()+"", "联系人"), weightLp());
        box.addView(sectionTitle("已导入通讯录"));
        if (imported.isEmpty()) box.addView(emptyBox("暂无已导入联系人", "从客户详情或批量导入功能写入手机通讯录"));
        for (CustomerDbHelper.ContactItem ct : imported) box.addView(importedContactCard(ct));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private View importedContactCard(CustomerDbHelper.ContactItem ct) {
        LinearLayout card = card();
        card.addView(text(ct.displayName(), 16, Color.rgb(22, 38, 64), true));
        card.addView(text("电话：" + ct.phone + "｜分组：" + ct.groupName + "｜状态：" + (CustomerDbHelper.empty(ct.customerStatus) ? "未设置" : ct.customerStatus), 13, Color.rgb(90, 103, 125), false));
        card.addView(text("导入时间：" + nvl(ct.importedAt, "未知"), 13, Color.rgb(90, 103, 125), false));
        LinearLayout ops = horizontal(0); card.addView(ops);
        ops.addView(smallButton("拨号", v -> dial(ct.phone)), weightLp());
        ops.addView(smallButton("查看企业", v -> showCompanyDetail(ct.companyId)), weightLp());
        ops.addView(smallButton("同步删除", v -> confirm("同步删除通讯录", "该操作会删除手机通讯录中由本App导入的联系人，但保留App内企业资料。", () -> deleteImportedContact(ct))), weightLp());
        return card;
    }

    private void renderImportExport() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(box);
        box.addView(titleRow("导入导出", "宽松导入、宽表导出，客户状态不从Excel导入"));
        box.addView(sectionTitle("Excel 导入"));
        box.addView(infoCard("导入规则", "只要有“公司名称”列即可导入；联系人、电话、备注、标签可动态识别；客户状态默认未设置，需要在App内手动设置。"));
        box.addView(primaryButton("选择 Excel 导入", v -> openImportFile()));
        box.addView(sectionTitle("Excel 导出"));
        box.addView(primaryButton("导出全部企业", v -> prepareExport("全部企业", db.getCompanies("", "", 0))));
        box.addView(primaryButton("按条件导出", v -> showExportDialog()));
        box.addView(primaryButton("下载导入模板", v -> exportTemplate()));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderSettings() {
        ScrollView scroll = scroll();
        LinearLayout box = vertical(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(box);
        box.addView(titleRow("我的", "分组、标签、规则与数据维护"));
        box.addView(sectionTitle("当前规则"));
        box.addView(infoCard("核心规则", "公司名称唯一识别；客户状态默认未设置且只能手动设置；通讯录名称为“公司名称-联系人姓名”；删除通讯录仅删除本App导入的联系人。"));
        box.addView(sectionTitle("分组管理"));
        for (CustomerDbHelper.GroupItem g : db.getGroups()) {
            LinearLayout row = horizontal(0); row.setPadding(0, dp(4), 0, dp(4));
            row.addView(text(g.name, 15, Color.rgb(42, 58, 86), true), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(smallButton("改名", v -> showRenameGroup(g)));
            row.addView(smallButton("删除", v -> confirm("删除分组", "删除分组后，企业会移入默认分组。", () -> { db.deleteGroup(g.id); render(); })));
            box.addView(row);
        }
        box.addView(primaryButton("新增分组", v -> showAddGroupDialog()));
        box.addView(sectionTitle("使用说明"));
        box.addView(infoCard("流程", "1. 导入Excel建立企业库\n2. 在客户页搜索、筛选、标记关注/跟进/重点\n3. 按分组或序号区间批量导入通讯录\n4. 在通讯录页查看已导入记录并可同步删除\n5. 定期导出Excel备份"));
        box.addView(primaryButton("清空全部App数据", v -> confirm("危险操作", "清空后无法恢复，且不会删除手机通讯录。确认清空？", () -> { deleteDatabase(CustomerDbHelper.DB_NAME); db = new CustomerDbHelper(this); render(); })));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showAddGroupDialog() {
        EditText e = edit("分组名称", "");
        new AlertDialog.Builder(this).setTitle("新增分组").setView(e).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{ db.ensureGroup(e.getText().toString()); render(); }).show();
    }

    private void showRenameGroup(CustomerDbHelper.GroupItem g) {
        EditText e = edit("分组名称", g.name);
        new AlertDialog.Builder(this).setTitle("重命名分组").setView(e).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{ db.renameGroup(g.id, e.getText().toString()); render(); }).show();
    }

    private void openImportFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try { startActivityForResult(i, REQ_IMPORT_XLSX); }
        catch (Exception e) {
            Intent any = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            any.addCategory(Intent.CATEGORY_OPENABLE);
            any.setType("*/*");
            startActivityForResult(any, REQ_IMPORT_XLSX);
        }
    }

    private void handleIncomingFile(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        if (uri != null) showImportOptions(uri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_IMPORT_XLSX) showImportOptions(uri);
        else if (requestCode == REQ_EXPORT_XLSX) writePendingExport(uri);
    }

    private void showImportOptions(Uri uri) {
        LinearLayout box = vertical(dp(10)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        List<CustomerDbHelper.GroupItem> groups = db.getGroups();
        Spinner spinner = new Spinner(this);
        ArrayAdapter<CustomerDbHelper.GroupItem> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        CheckBox useExcelGroup = new CheckBox(this);
        useExcelGroup.setText("如果Excel中有“分组”列，优先使用Excel分组");
        useExcelGroup.setChecked(true);
        box.addView(text("选择默认分组", 14, Color.rgb(42, 58, 86), true));
        box.addView(spinner);
        box.addView(useExcelGroup);
        box.addView(text("说明：客户状态不会从Excel导入，所有新企业默认未设置。", 13, Color.rgb(125, 93, 20), false));
        new AlertDialog.Builder(this)
                .setTitle("导入Excel")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始导入", (d,w)->{
                    CustomerDbHelper.GroupItem g = (CustomerDbHelper.GroupItem) spinner.getSelectedItem();
                    importExcel(uri, g == null ? db.ensureGroup("默认分组") : g.id, useExcelGroup.isChecked());
                }).show();
    }

    private void importExcel(Uri uri, long groupId, boolean useExcelGroup) {
        try {
            List<Map<String, String>> rows = XlsxSimpleReader.readFirstSheet(getContentResolver(), uri);
            CustomerDbHelper.ImportResult r = db.importRows(rows, groupId, useExcelGroup);
            new AlertDialog.Builder(this)
                    .setTitle("导入完成")
                    .setMessage("新增企业：" + r.newCompanies + "\n合并企业：" + r.mergedCompanies + "\n新增联系人：" + r.newContacts + "\n新增备注：" + r.newNotes + "\n跳过无公司名称行：" + r.skippedNoCompany)
                    .setPositiveButton("确定", (d,w)->render())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("导入失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show();
        }
    }

    private void showExportDialog() {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        List<String> options = new ArrayList<>();
        options.add("全部企业"); options.add("未设置状态"); options.add("关注客户"); options.add("跟进客户"); options.add("重点客户");
        for (CustomerDbHelper.GroupItem g : db.getGroups()) options.add("分组：" + g.name);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad); box.addView(sp);
        new AlertDialog.Builder(this).setTitle("选择导出范围").setView(box).setNegativeButton("取消", null).setPositiveButton("导出", (d,w)->{
            String opt = (String) sp.getSelectedItem();
            List<CustomerDbHelper.CompanyItem> companies;
            if ("未设置状态".equals(opt)) companies = db.getCompanies("", CustomerDbHelper.STATUS_NONE, 0);
            else if ("关注客户".equals(opt)) companies = db.getCompanies("", CustomerDbHelper.STATUS_FOCUS, 0);
            else if ("跟进客户".equals(opt)) companies = db.getCompanies("", CustomerDbHelper.STATUS_FOLLOW, 0);
            else if ("重点客户".equals(opt)) companies = db.getCompanies("", CustomerDbHelper.STATUS_IMPORTANT, 0);
            else if (opt != null && opt.startsWith("分组：")) {
                String name = opt.substring(3);
                long gid = 0; for (CustomerDbHelper.GroupItem g : db.getGroups()) if (g.name.equals(name)) gid = g.id;
                companies = db.getCompanies("", "", gid);
            } else companies = db.getCompanies("", "", 0);
            prepareExport(opt, companies);
        }).show();
    }

    private void prepareExport(String title, List<CustomerDbHelper.CompanyItem> companies) {
        buildExportData(companies);
        String fileName = "企业客户管家_" + title.replace("：", "_") + "_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date()) + ".xlsx";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(i, REQ_EXPORT_XLSX);
    }

    private void exportTemplate() {
        pendingExportHeaders = Arrays.asList("序号", "分组", "公司名称", "所属行业", "参保人数", "区域", "地址", "联系人1", "电话号码1", "联系人2", "电话号码2", "备注1", "备注2", "标签1", "标签2");
        pendingExportRows = new ArrayList<>();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE, "企业客户管家_导入模板.xlsx");
        startActivityForResult(i, REQ_EXPORT_XLSX);
    }

    private void buildExportData(List<CustomerDbHelper.CompanyItem> companies) {
        int maxContacts = 1, maxNotes = 1, maxTags = 1;
        Map<Long, List<CustomerDbHelper.ContactItem>> contacts = db.contactsByCompany(companies);
        Map<Long, List<CustomerDbHelper.NoteItem>> notes = db.notesByCompany(companies);
        for (CustomerDbHelper.CompanyItem c : companies) {
            maxContacts = Math.max(maxContacts, contacts.get(c.id).size());
            maxNotes = Math.max(maxNotes, notes.get(c.id).size());
            maxTags = Math.max(maxTags, splitSemi(c.tags).size());
        }
        ArrayList<String> headers = new ArrayList<>();
        headers.addAll(Arrays.asList("序号", "分组", "客户状态", "公司名称", "所属行业", "参保人数", "区域", "地址"));
        for (int i=1;i<=maxContacts;i++) { headers.add("联系人" + i); headers.add("电话号码" + i); }
        for (int i=1;i<=maxNotes;i++) headers.add("备注" + i);
        for (int i=1;i<=maxTags;i++) headers.add("标签" + i);
        headers.add("扩展信息"); headers.add("创建时间"); headers.add("更新时间");
        ArrayList<List<String>> rows = new ArrayList<>();
        for (CustomerDbHelper.CompanyItem c : companies) {
            ArrayList<String> row = new ArrayList<>();
            row.add(String.valueOf(c.seq)); row.add(nvl(c.groupName,"")); row.add(c.statusText()); row.add(nvl(c.name,""));
            row.add(nvl(c.industry,"")); row.add(nvl(c.employeeCount,"")); row.add(nvl(c.region,"")); row.add(nvl(c.address,""));
            List<CustomerDbHelper.ContactItem> cl = contacts.get(c.id);
            for (int i=0;i<maxContacts;i++) {
                if (i < cl.size()) { row.add(nvl(cl.get(i).contactName,"")); row.add(nvl(cl.get(i).phone,"")); }
                else { row.add(""); row.add(""); }
            }
            List<CustomerDbHelper.NoteItem> nl = notes.get(c.id);
            for (int i=0;i<maxNotes;i++) row.add(i < nl.size() ? nl.get(i).content : "");
            List<String> tags = splitSemi(c.tags);
            for (int i=0;i<maxTags;i++) row.add(i < tags.size() ? tags.get(i) : "");
            row.add(nvl(c.extraInfo,"")); row.add(nvl(c.createdAt,"")); row.add(nvl(c.updatedAt,""));
            rows.add(row);
        }
        pendingExportHeaders = headers;
        pendingExportRows = rows;
    }

    private void writePendingExport(Uri uri) {
        try {
            XlsxSimpleWriter.write(getContentResolver(), uri, pendingExportHeaders, pendingExportRows);
            Toast.makeText(this, "导出成功", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("导出失败").setMessage(e.getMessage()).setPositiveButton("确定", null).show();
        }
    }

    private void importCompanyContacts(long companyId) {
        List<CustomerDbHelper.ContactItem> list = db.getContacts(companyId, false);
        if (list.isEmpty()) { toast("没有未导入联系人"); return; }
        importContactsWithPreview(list);
    }

    private void showBulkImportDialog() {
        LinearLayout box = vertical(dp(8)); box.setPadding(dp(18), dp(8), dp(18), dp(8));
        List<CustomerDbHelper.GroupItem> groups = db.getGroups();
        Spinner groupSp = new Spinner(this);
        ArrayAdapter<CustomerDbHelper.GroupItem> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); groupSp.setAdapter(ad);
        EditText start = edit("起始企业序号", "1"); start.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText end = edit("结束企业序号", "20"); end.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(text("按分组企业序号区间导入", 14, Color.rgb(42,58,86), true));
        box.addView(groupSp); box.addView(start); box.addView(end);
        new AlertDialog.Builder(this).setTitle("批量导入通讯录").setView(box).setNegativeButton("取消", null).setPositiveButton("预览", (d,w)->{
            CustomerDbHelper.GroupItem g = (CustomerDbHelper.GroupItem) groupSp.getSelectedItem();
            int s = parseInt(start.getText().toString(), 1); int e = parseInt(end.getText().toString(), s);
            List<CustomerDbHelper.ContactItem> list = db.getUnimportedContactsByRange(g.id, s, e);
            importContactsWithPreview(list);
        }).show();
    }

    private void importContactsWithPreview(List<CustomerDbHelper.ContactItem> list) {
        if (list == null || list.isEmpty()) { toast("没有可导入的联系人"); return; }
        StringBuilder sb = new StringBuilder();
        int preview = Math.min(8, list.size());
        for (int i=0;i<preview;i++) sb.append(list.get(i).displayName()).append("  ").append(list.get(i).phone).append("\n");
        if (list.size() > preview) sb.append("……共 ").append(list.size()).append(" 个联系人");
        new AlertDialog.Builder(this)
                .setTitle("确认导入通讯录")
                .setMessage("本次将导入 " + list.size() + " 个联系人。\n\n" + sb + "\n\n已导入联系人会自动跳过。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始导入", (d,w)->{
                    pendingImportContacts = list;
                    ensureContactsPermissionThenImport();
                }).show();
    }

    private void ensureContactsPermissionThenImport() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS}, REQ_CONTACT_PERM);
        } else importPendingContacts();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACT_PERM) {
            boolean ok = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) importPendingContacts(); else toast("需要通讯录权限才能导入和同步删除");
        }
    }

    private void importPendingContacts() {
        if (pendingImportContacts == null) return;
        int success = 0, skipped = 0, failed = 0;
        for (CustomerDbHelper.ContactItem ct : pendingImportContacts) {
            if (ct.imported) { skipped++; continue; }
            if (phoneExistsInDevice(ct.phoneNorm)) { skipped++; continue; }
            try {
                long rawId = insertContactToDevice(ct.displayName(), ct.phone, ct.companyName);
                if (rawId > 0) { db.markContactImported(ct.id, rawId); success++; }
                else failed++;
            } catch (Exception e) { failed++; }
        }
        pendingImportContacts = null;
        new AlertDialog.Builder(this).setTitle("导入完成").setMessage("成功：" + success + "\n跳过：" + skipped + "\n失败：" + failed).setPositiveButton("确定", (d,w)->render()).show();
    }

    private long insertContactToDevice(String displayName, String phone, String company) throws Exception {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK).build());
        ContentProviderResult[] res = getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        if (res != null && res.length > 0 && res[0].uri != null) return ContentUris.parseId(res[0].uri);
        return -1;
    }

    private boolean phoneExistsInDevice(String phoneNorm) {
        if (CustomerDbHelper.empty(phoneNorm) || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        android.database.Cursor c = getContentResolver().query(uri, projection, null, null, null);
        if (c == null) return false;
        try {
            while (c.moveToNext()) {
                String n = CustomerDbHelper.normalizePhone(c.getString(0));
                if (phoneNorm.equals(n)) return true;
            }
        } finally { c.close(); }
        return false;
    }

    private void deleteImportedContact(CustomerDbHelper.ContactItem ct) {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingImportContacts = null;
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS}, REQ_CONTACT_PERM);
            return;
        }
        if (ct.rawContactId > 0) {
            try {
                Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, ct.rawContactId);
                getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {}
        }
        db.markContactUnimported(ct.id);
        render();
    }

    private void dial(String phone) {
        try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))); }
        catch (Exception e) { toast("无法打开拨号器"); }
    }

    private List<String> splitSemi(String s) {
        ArrayList<String> list = new ArrayList<>();
        if (s == null) return list;
        for (String p : s.split("；|;")) if (!CustomerDbHelper.empty(p)) list.add(p.trim());
        return list;
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch(Exception e){ return def; } }
    private String nvl(String s, String def) { return CustomerDbHelper.empty(s) ? def : s; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void confirm(String title, String msg, Runnable ok) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("取消", null).setPositiveButton("确认", (d,w)->ok.run()).show();
    }

    private LinearLayout vertical(int gap) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE); return l; }
    private LinearLayout horizontal(int gap) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private ScrollView scroll() { ScrollView s = new ScrollView(this); s.setFillViewport(false); return s; }
    private LinearLayout.LayoutParams weightLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(4), dp(4), dp(4), dp(4)); return lp; }

    private View header(String title, String subtitle) {
        LinearLayout h = vertical(dp(4));
        h.setPadding(dp(18), dp(18), dp(18), dp(18));
        h.setBackground(round(Color.rgb(30, 94, 255), dp(18), 0));
        h.addView(text(title, 24, Color.WHITE, true));
        h.addView(text(subtitle, 13, Color.rgb(230, 237, 255), false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(12)); h.setLayoutParams(lp);
        return h;
    }

    private View titleRow(String title, String subtitle) {
        LinearLayout row = vertical(0);
        row.addView(text(title, 22, Color.rgb(22, 38, 64), true));
        row.addView(text(subtitle, 13, Color.rgb(90, 103, 125), false));
        row.setPadding(0, 0, 0, dp(10));
        return row;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s == null ? "" : s); v.setTextSize(sp); v.setTextColor(color); v.setLineSpacing(dp(2), 1.0f); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v;
    }

    private TextView sectionTitle(String s) {
        TextView v = text(s, 16, Color.rgb(22, 38, 64), true); v.setPadding(0, dp(14), 0, dp(8)); return v;
    }

    private LinearLayout statCard(String title, String value, String suffix) {
        LinearLayout c = card();
        c.addView(text(title, 13, Color.rgb(90, 103, 125), false));
        c.addView(text(value, 24, Color.rgb(30, 94, 255), true));
        c.addView(text(suffix, 12, Color.rgb(130, 142, 160), false));
        return c;
    }

    private View infoCard(String title, String msg) {
        LinearLayout c = card(); c.addView(text(title, 15, Color.rgb(22, 38, 64), true)); c.addView(text(msg, 13, Color.rgb(90, 103, 125), false)); return c;
    }

    private LinearLayout card() {
        LinearLayout c = vertical(dp(6));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.setBackground(round(Color.WHITE, dp(14), Color.rgb(226, 232, 240)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(5), 0, dp(5)); c.setLayoutParams(lp);
        return c;
    }

    private TextView emptyBox(String title, String msg) {
        TextView v = text(title + "\n" + msg, 15, Color.rgb(120, 132, 150), false); v.setGravity(Gravity.CENTER); v.setPadding(dp(16), dp(32), dp(16), dp(32)); v.setBackground(round(Color.WHITE, dp(14), Color.rgb(226, 232, 240))); return v;
    }

    private TextView label(String s) {
        String t = CustomerDbHelper.empty(s) ? "未设置" : s;
        int color = Color.rgb(104, 116, 140);
        if (CustomerDbHelper.STATUS_FOCUS.equals(t)) color = Color.rgb(30, 94, 255);
        else if (CustomerDbHelper.STATUS_FOLLOW.equals(t)) color = Color.rgb(224, 122, 0);
        else if (CustomerDbHelper.STATUS_IMPORTANT.equals(t)) color = Color.rgb(220, 38, 38);
        TextView v = text(t, 12, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), dp(3), dp(8), dp(3)); v.setBackground(round(color, dp(999), 0)); return v;
    }

    private TextView chip(String s, boolean active, View.OnClickListener l) {
        TextView v = text(s, 13, active ? Color.WHITE : Color.rgb(42, 58, 86), true);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(7), dp(12), dp(7));
        v.setBackground(round(active ? Color.rgb(30,94,255) : Color.WHITE, dp(999), Color.rgb(220, 226, 236)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(4), 0, dp(4), 0); v.setLayoutParams(lp); v.setOnClickListener(l); return v;
    }

    private Button primaryButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(round(Color.rgb(30,94,255), dp(12), 0)); b.setOnClickListener(l); return b; }
    private Button primaryButton(String s) { return primaryButton(s, v -> {}); }
    private Button smallButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(Color.rgb(30,94,255)); b.setBackground(round(Color.rgb(239, 244, 255), dp(10), Color.rgb(200, 215, 255))); b.setOnClickListener(l); return b; }

    private EditText edit(String hint, String val) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(val == null ? "" : val); e.setTextSize(14); e.setSingleLine(false); e.setMinLines(1); e.setPadding(dp(10), 0, dp(10), 0); e.setBackground(round(Color.WHITE, dp(10), Color.rgb(220, 226, 236))); return e;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (strokeColor != 0) g.setStroke(dp(1), strokeColor); return g;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
