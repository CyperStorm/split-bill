package my.splitmakan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final int PAPER = 0xfff3f7f6;
    private static final int GREEN = 0xff0e5b4c;
    private static final int DARK_GREEN = 0xff103f37;
    private static final int INK = 0xff172f29;
    private static final int MUTED = 0xff526960;
    private static final int MINT = 0xffe1f2eb;
    private static final int LINE = 0xffd9e5df;
    private static final int AMBER = 0xffffefd3;
    private static final int AMBER_INK = 0xff724818;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int DECIMAL = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

    private static final class Diner {
        String name;
        long received;
        Diner(String name, long received) { this.name = name; this.received = received; }
    }

    private static final class Item {
        String name = "";
        String price = "";
        final Set<Integer> diners = new LinkedHashSet<>();
    }

    private static final class Split {
        long[] food, tax, service, adjustment, total;
        long subtotal, taxTotal, serviceTotal, adjustmentTotal, grandTotal;
    }

    private final List<Diner> diners = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private String sst = "", service = "", adjustment = "";
    private String restaurant = "", billId = "";
    private JSONArray history = new JSONArray();
    private enum Page { EDIT, SPLIT, HISTORY }
    private LinearLayout body;
    private ScrollView scroll;
    private Page page = Page.EDIT;
    private Page historyReturn = Page.EDIT;
    private final OnBackInvokedCallback backToBill = () -> {
        if (page == Page.HISTORY && historyReturn == Page.SPLIT) review(false);
        else edit(false);
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        restore();
        edit(false);
        if (state != null) {
            Page restored = Page.valueOf(state.getString("page", Page.EDIT.name()));
            historyReturn = Page.valueOf(state.getString("historyReturn", Page.EDIT.name()));
            if (restored == Page.SPLIT) review(false);
            if (restored == Page.HISTORY) showHistory();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("page", page.name());
        state.putString("historyReturn", historyReturn.name());
    }

    private void defaults() {
        diners.clear();
        items.clear();
        diners.add(new Diner("Me", 0));
        diners.add(new Diner("Diner 2", 0));
        Item item = new Item();
        item.diners.add(0);
        items.add(item);
        sst = service = adjustment = "";
        restaurant = billId = "";
    }

    private void restore() {
        defaults();
        try {
            history = new JSONArray(getPreferences(MODE_PRIVATE).getString("history", "[]"));
        } catch (JSONException malformed) {
            history = new JSONArray();
        }
        String saved = getPreferences(MODE_PRIVATE).getString("bill", "");
        if (saved.isEmpty()) return;
        try {
            loadBill(new JSONObject(saved));
        } catch (JSONException malformed) {
            defaults();
        }
    }

    private void loadBill(JSONObject bill) throws JSONException {
            List<Diner> loadedDiners = new ArrayList<>();
            List<Item> loadedItems = new ArrayList<>();
            JSONArray people = bill.getJSONArray("diners");
            for (int i = 0; i < people.length(); i++) {
                JSONObject diner = people.getJSONObject(i);
                loadedDiners.add(new Diner(diner.getString("name"), diner.getLong("received")));
            }
            JSONArray lines = bill.getJSONArray("items");
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.getJSONObject(i);
                Item item = new Item();
                item.name = line.getString("name");
                item.price = line.getString("price");
                JSONArray owners = line.getJSONArray("diners");
                for (int j = 0; j < owners.length(); j++) item.diners.add(owners.getInt(j));
                loadedItems.add(item);
            }
            sst = bill.getString("sst");
            service = bill.getString("service");
            adjustment = bill.getString("adjustment");
            restaurant = bill.optString("restaurant");
            billId = bill.optString("id");
            diners.clear();
            diners.addAll(loadedDiners);
            items.clear();
            items.addAll(loadedItems);
    }

    private JSONObject billSnapshot() {
        try {
            JSONArray people = new JSONArray(), lines = new JSONArray();
            for (Diner diner : diners) {
                people.put(new JSONObject().put("name", diner.name).put("received", diner.received));
            }
            for (Item item : items) {
                lines.put(new JSONObject().put("name", item.name).put("price", item.price)
                        .put("diners", new JSONArray(item.diners)));
            }
            return new JSONObject().put("diners", people).put("items", lines)
                    .put("sst", sst).put("service", service).put("adjustment", adjustment)
                    .put("restaurant", restaurant).put("id", billId);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void save() {
        getPreferences(MODE_PRIVATE).edit().putString("bill", billSnapshot().toString()).apply();
    }

    private int savedIndex() {
        for (int i = 0; i < history.length(); i++) {
            if (history.optJSONObject(i).optJSONObject("bill").optString("id").equals(billId)) return i;
        }
        return -1;
    }

    private void saveToHistory(Split split) {
        int previous = savedIndex();
        long created = previous < 0 ? System.currentTimeMillis() : history.optJSONObject(previous).optLong("date");
        if (billId.isEmpty()) billId = UUID.randomUUID().toString();
        try {
            JSONObject bill = billSnapshot();
            JSONObject entry = new JSONObject().put("bill", bill).put("date", created)
                    .put("total", split.grandTotal).put("outstanding", outstanding(split));
            if (previous >= 0) history.remove(previous);
            history.put(entry);
            getPreferences(MODE_PRIVATE).edit().putString("bill", bill.toString())
                    .putString("history", history.toString()).apply();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean hasUnsavedBill() {
        int index = savedIndex();
        if (index >= 0) {
            return !history.optJSONObject(index).optJSONObject("bill").toString().equals(billSnapshot().toString());
        }
        return !restaurant.isBlank() || !sst.isBlank() || !service.isBlank() || !adjustment.isBlank()
                || items.stream().anyMatch(item -> !item.name.isBlank() || !item.price.isBlank());
    }

    private void switchBill(String title, Runnable action) {
        if (!hasUnsavedBill()) {
            action.run();
            return;
        }
        AlertDialog.Builder confirm = new AlertDialog.Builder(this).setTitle(title)
                .setMessage("The current bill has changes that are only in this draft.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Discard changes", (dialog, which) -> action.run());
        try {
            Split split = calculate();
            confirm.setPositiveButton("Save & continue", (dialog, which) -> {
                saveToHistory(split);
                action.run();
            });
        } catch (IllegalArgumentException incomplete) {
            confirm.setMessage("Finish the current bill before saving it to history.");
        }
        confirm.show();
    }

    private void navigate(Page next) {
        if (page == Page.EDIT && next != Page.EDIT) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backToBill);
        } else if (page != Page.EDIT && next == Page.EDIT) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backToBill);
        }
        page = next;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void screen(String eyebrow, String title, String subtitle, boolean preserveScroll) {
        int oldY = preserveScroll && scroll != null ? scroll.getScrollY() : 0;
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            var keyboard = insets.getInsets(WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        body = column();
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        body.setFocusableInTouchMode(true);
        scroll.addView(body);
        setContentView(scroll);
        body.requestFocus();

        LinearLayout hero = column();
        hero.setPadding(dp(20), dp(16), dp(20), dp(18));
        hero.setBackground(background(DARK_GREEN, 22, 0));
        body.addView(hero);
        label(hero, eyebrow, 12, MINT, true, 0).setLetterSpacing(0.1f);
        label(hero, title, 32, Color.WHITE, true, 4);
        label(hero, subtitle, 15, MINT, false, 5);
        ScrollView current = scroll;
        current.post(() -> current.scrollTo(0, oldY));
    }

    private TextView label(LinearLayout parent, String value, int size, int color, boolean bold, int top) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(text, params);
        return text;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(12), dp(15), dp(14));
        card.setBackground(background(Color.WHITE, 18, LINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        body.addView(card, params);
        return card;
    }

    private LinearLayout row(LinearLayout parent, int top) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(row, params);
        return row;
    }

    private void fillRow(View child, float weight, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight);
        params.setMarginEnd(dp(gap));
        child.setLayoutParams(params);
    }

    private Button button(LinearLayout parent, String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setBackground(background(primary ? GREEN : MINT, 12, 0));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private void badge(LinearLayout parent, String title, boolean pending) {
        TextView text = label(parent, title, 14, pending ? AMBER_INK : GREEN, true, 10);
        text.getLayoutParams().width = -2;
        text.setPadding(dp(10), dp(5), dp(10), dp(5));
        text.setBackground(background(pending ? AMBER : MINT, 12, 0));
    }

    private void field(LinearLayout parent, String title, String hint, String value,
                       int inputType, int maxLength, Consumer<String> changed) {
        TextView caption = label(parent, title, 14, MUTED, true, 8);
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        caption.setLabelFor(input.getId());
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setBackground(background(PAPER, 10, LINE));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        input.setHint(hint);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setMinHeight(dp(48));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                changed.accept(s.toString());
                save();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(3);
        parent.addView(input, params);
    }

    private String dinerName(int i) {
        String name = diners.get(i).name.trim();
        return name.isEmpty() ? "Diner " + (i + 1) : name;
    }

    private String owners(Item item) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < diners.size(); i++) if (item.diners.contains(i)) names.add(dinerName(i));
        return names.isEmpty() ? "Choose diners" : "For " + String.join(", ", names);
    }

    private void edit(boolean preserveScroll) {
        navigate(Page.EDIT);
        screen("MALAYSIAN RINGGIT  /  MYR", "Split Bill",
                "Enter the receipt and see what each diner owes.", preserveScroll);
        LinearLayout navigation = row(body, 10);
        fillRow(button(navigation, "Bill history", false, this::openHistory), 1, 8);
        fillRow(button(navigation, "New bill", false, this::newBill), 1, 0);
        field(card(), "Restaurant or bill name", "Optional, e.g. Friday dinner", restaurant,
                TEXT, 60, value -> restaurant = value);
        label(body, "01  Diners", 21, INK, true, 18);
        LinearLayout people = card();
        for (int i = 0; i < diners.size(); i++) {
            int index = i;
            Diner diner = diners.get(i);
            LinearLayout personRow = row(people, i == 0 ? 0 : 5);
            LinearLayout name = column();
            personRow.addView(name);
            fillRow(name, 1, 8);
            field(name, i == 0 ? "Diner 1 · Bill payer" : "Diner " + (i + 1), "Name", diner.name, TEXT, 40, value -> {
                diner.name = value;
                refreshOwnerLabels();
            });
            if (i > 0 && diner.received == 0) {
                Button remove = button(personRow, "Remove", false, () -> removeDiner(index));
                remove.setContentDescription("Remove diner " + (i + 1));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.gravity = Gravity.BOTTOM;
                remove.setLayoutParams(params);
            }
        }
        if (diners.size() < 30) button(people, "+ Add diner", false, () -> {
            diners.add(new Diner("Diner " + (diners.size() + 1), 0));
            save();
            edit(true);
        });

        label(body, "02  Ordered items", 21, INK, true, 19);
        label(body, "Choose more than one diner for a shared item.", 15, MUTED, false, 4);
        ownerButtons.clear();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            LinearLayout line = card();
            label(line, "ITEM " + (i + 1), 12, GREEN, true, 0);
            LinearLayout fields = row(line, 0);
            LinearLayout name = column(), price = column();
            fields.addView(name);
            fields.addView(price);
            fillRow(name, 3, 8);
            fillRow(price, 2, 0);
            field(name, "Item name", "e.g. Nasi lemak", item.name, TEXT, 70, value -> item.name = value);
            field(price, "Price (RM)", "0.00", item.price, DECIMAL, 10, value -> item.price = value);
            LinearLayout actions = row(line, 8);
            Button owners = button(actions, owners(item), false, () -> chooseDiners(item));
            ownerButtons.add(owners);
            fillRow(owners, 3, 8);
            Button remove = button(actions, "Remove", false, () -> {
                items.remove(item);
                save();
                edit(true);
            });
            remove.setContentDescription("Remove item " + (i + 1));
            fillRow(remove, 2, 0);
        }
        if (items.size() < 100) button(body, "+ Add item", false, () -> {
            items.add(new Item());
            save();
            edit(true);
        });

        label(body, "03  Receipt charges", 21, INK, true, 19);
        LinearLayout charges = card();
        label(charges, "Enter each charge as an RM amount or add % to use a rate. Leave blank if absent.", 14, MUTED, false, 0);
        field(charges, "SST (service tax)", "e.g. 3.60 or 6%", sst, InputType.TYPE_CLASS_TEXT, 14, value -> sst = value);
        field(charges, "Restaurant service charge", "e.g. 6.00 or 10%", service,
                InputType.TYPE_CLASS_TEXT, 14, value -> service = value);
        field(charges, "Receipt adjustment (RM)", "e.g. -0.02 for rounding", adjustment,
                DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED, 11, value -> adjustment = value);
        button(body, "Calculate split", true, () -> review(false));
    }

    private void newBill() {
        switchBill("Start a new bill?", () -> {
            defaults();
            save();
            edit(false);
        });
    }

    private void openHistory() {
        historyReturn = page;
        showHistory();
    }

    private void showHistory() {
        navigate(Page.HISTORY);
        screen("SPLIT BILL  /  MYR", "Bill history",
                "Reopen a saved receipt to check who still owes you.", false);
        button(body, "Back to current bill", false, backToBill::onBackInvoked);
        if (history.length() == 0) {
            LinearLayout empty = card();
            label(empty, "No saved bills yet", 19, INK, true, 0);
            label(empty, "After calculating a split, tap Save bill to add it here.", 15, MUTED, false, 5);
            return;
        }
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                Locale.forLanguageTag("en-MY"));
        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject entry = history.optJSONObject(i);
            JSONObject bill = entry.optJSONObject("bill");
            long balance = entry.optLong("outstanding");
            LinearLayout receipt = card();
            label(receipt, billTitle(bill.optString("restaurant")), 19, INK, true, 0);
            label(receipt, date.format(new Date(entry.optLong("date"))), 14, MUTED, false, 3);
            amount(receipt, "Receipt total", entry.optLong("total"));
            badge(receipt, balanceLabel(balance), balance > 0);
            button(receipt, "Open bill", false, () -> openSavedBill(bill));
        }
    }

    private void openSavedBill(JSONObject bill) {
        switchBill("Open saved bill?", () -> {
            try {
                int active = savedIndex();
                JSONObject selected = active >= 0 && bill.optString("id").equals(billId)
                        ? history.optJSONObject(active).getJSONObject("bill") : bill;
                loadBill(selected);
                save();
                review(false);
            } catch (JSONException invalid) {
                Toast.makeText(this, "This saved bill could not be opened.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String billTitle(String name) {
        return name.isBlank() ? "Restaurant bill" : name.trim();
    }

    private final List<Button> ownerButtons = new ArrayList<>();

    private void refreshOwnerLabels() {
        for (int i = 0; i < ownerButtons.size(); i++) ownerButtons.get(i).setText(owners(items.get(i)));
    }

    private void removeDiner(int index) {
        new AlertDialog.Builder(this).setTitle("Remove " + dinerName(index) + "?")
                .setMessage("Check the diner assignments on any items they ordered.")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> {
                    diners.remove(index);
                    for (Item item : items) {
                        Set<Integer> updated = new LinkedHashSet<>();
                        for (int owner : item.diners) if (owner != index) {
                            updated.add(owner > index ? owner - 1 : owner);
                        }
                        item.diners.clear();
                        item.diners.addAll(updated);
                    }
                    save();
                    edit(true);
                }).show();
    }

    private void chooseDiners(Item item) {
        String[] names = new String[diners.size()];
        boolean[] checked = new boolean[diners.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = dinerName(i);
            checked[i] = item.diners.contains(i);
        }
        new AlertDialog.Builder(this).setTitle("Who shared this item?")
                .setMultiChoiceItems(names, checked, (dialog, which, selected) -> checked[which] = selected)
                .setNegativeButton("Cancel", null).setPositiveButton("Done", (dialog, which) -> {
                    item.diners.clear();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) item.diners.add(i);
                    save();
                    refreshOwnerLabels();
                }).show();
    }

    private long readCharge(String name, String value, long subtotal) {
        try {
            return BillMath.charge(value, subtotal);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(name + " needs an RM amount or a rate from 0% to 100%.");
        }
    }

    private Split calculate() {
        Split split = new Split();
        int count = diners.size();
        split.food = new long[count];
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.price.isBlank() && item.name.isBlank()) continue;
            String itemLabel = item.name.isBlank() ? "Item " + (i + 1) : item.name.trim();
            long price;
            try {
                price = BillMath.money(item.price, false);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(itemLabel + " needs a valid RM price.");
            }
            if (item.diners.isEmpty()) throw new IllegalArgumentException("Choose who ordered " + itemLabel + ".");
            long[] weights = new long[count];
            for (int owner : item.diners) weights[owner] = 1;
            long[] shares = BillMath.allocate(price, weights);
            for (int j = 0; j < count; j++) split.food[j] += shares[j];
            split.subtotal += price;
        }
        if (split.subtotal == 0) throw new IllegalArgumentException("Add at least one priced item.");
        split.taxTotal = readCharge("SST", sst, split.subtotal);
        split.serviceTotal = readCharge("Service charge", service, split.subtotal);
        try {
            split.adjustmentTotal = adjustment.isBlank() ? 0 : BillMath.money(adjustment, true);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Enter the receipt adjustment as an RM amount.");
        }
        split.tax = BillMath.allocate(split.taxTotal, split.food);
        split.service = BillMath.allocate(split.serviceTotal, split.food);
        split.total = new long[count];
        for (int i = 0; i < count; i++) {
            split.total[i] = split.food[i] + split.tax[i] + split.service[i];
        }
        long beforeAdjustment = split.subtotal + split.taxTotal + split.serviceTotal;
        split.grandTotal = beforeAdjustment + split.adjustmentTotal;
        if (split.grandTotal < 0) throw new IllegalArgumentException("The adjustment exceeds the bill total.");
        split.adjustment = BillMath.allocate(split.adjustmentTotal,
                split.adjustmentTotal < 0 ? split.total : split.food);
        for (int i = 0; i < count; i++) split.total[i] += split.adjustment[i];
        return split;
    }

    private void review(boolean preserveScroll) {
        Split split;
        try {
            split = calculate();
        } catch (IllegalArgumentException invalid) {
            new AlertDialog.Builder(this).setTitle("Check the bill").setMessage(invalid.getMessage())
                    .setPositiveButton("OK", null).show();
            return;
        }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
                body.getWindowToken(), 0);
        navigate(Page.SPLIT);
        screen("RECEIPT TOTAL  /  MYR", BillMath.rm(split.grandTotal),
                billTitle(restaurant) + " · " + diners.size() + (diners.size() == 1 ? " diner" : " diners"),
                preserveScroll);
        LinearLayout actions = row(body, 10);
        fillRow(button(actions, "Share result", true, () -> share(split)), 1, 8);
        boolean saved = savedIndex() >= 0;
        fillRow(button(actions, saved ? "Update saved bill" : "Save bill", false, () -> {
            saveToHistory(split);
            Toast.makeText(this, "Saved to bill history", Toast.LENGTH_SHORT).show();
            review(true);
        }), 1, 0);
        LinearLayout navigation = row(body, 7);
        fillRow(button(navigation, "Edit bill", false, () -> edit(false)), 1, 8);
        fillRow(button(navigation, "Bill history", false, this::openHistory), 1, 0);
        LinearLayout totals = card();
        label(totals, "Receipt breakdown", 18, INK, true, 0);
        amount(totals, "Items", split.subtotal);
        amount(totals, "SST", split.taxTotal);
        amount(totals, "Service charge", split.serviceTotal);
        if (split.adjustmentTotal != 0) amount(totals, "Adjustment", split.adjustmentTotal);
        long outstanding = outstanding(split);
        badge(totals, balanceLabel(outstanding), outstanding > 0);
        label(body, "Who pays what", 21, INK, true, 17);
        for (int i = 0; i < diners.size(); i++) {
            int index = i;
            Diner diner = diners.get(i);
            LinearLayout person = card();
            LinearLayout heading = row(person, 0);
            fillRow(label(heading, dinerName(i), 18, INK, true, 0), 1, 8);
            TextView total = label(heading, BillMath.rm(split.total[i]), 19, GREEN, true, 0);
            total.getLayoutParams().width = -2;
            if (i == 0) label(person, "Bill payer", 13, MUTED, false, 0);
            amount(person, "Items", split.food[i]);
            amount(person, "SST", split.tax[i]);
            amount(person, "Service charge", split.service[i]);
            if (split.adjustment[i] != 0) amount(person, "Adjustment", split.adjustment[i]);
            if (i == 0) continue;
            long balance = split.total[i] - diner.received;
            if (diner.received > 0) amount(person, "Received", diner.received);
            if (balance > 0) {
                badge(person, "Owes " + BillMath.rm(balance), true);
                button(person, "Mark paid in full", true, () -> {
                    diner.received = split.total[index];
                    savePayment(split);
                    review(true);
                });
            } else if (balance < 0) {
                badge(person, "Refund " + BillMath.rm(-balance), true);
            } else if (diner.received > 0) {
                badge(person, "Paid in full", false);
            }
            if (split.total[i] > 0 || diner.received > 0) {
                button(person, "Edit amount received", false, () -> payment(diner, index, split));
            }
        }
        button(body, "New bill", false, this::newBill);
    }

    private long outstanding(Split split) {
        long outstanding = 0;
        for (int i = 1; i < diners.size(); i++) {
            outstanding += Math.max(0, split.total[i] - diners.get(i).received);
        }
        return outstanding;
    }

    private String balanceLabel(long outstanding) {
        return outstanding == 0 ? "Nothing to collect" : "Still to collect · " + BillMath.rm(outstanding);
    }

    private void savePayment(Split split) {
        if (savedIndex() >= 0) saveToHistory(split);
        else save();
    }

    private void amount(LinearLayout parent, String name, long sen) {
        LinearLayout amounts = row(parent, 3);
        fillRow(label(amounts, name, 14, MUTED, false, 0), 1, 8);
        label(amounts, BillMath.rm(sen), 14, INK, false, 0).getLayoutParams().width = -2;
    }

    private void payment(Diner diner, int index, Split split) {
        EditText input = new EditText(this);
        input.setInputType(DECIMAL);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        input.setText(java.math.BigDecimal.valueOf(diner.received, 2).toPlainString());
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Received from " + dinerName(index) + " (RM)")
                .setMessage("Enter the total received so far.")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(event -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        diner.received = BillMath.money(input.getText().toString(), false);
                        savePayment(split);
                        dialog.dismiss();
                        review(true);
                    } catch (IllegalArgumentException invalid) {
                        input.setError("Enter an RM amount, such as 12.50.");
                    }
                }));
        dialog.show();
    }

    private void share(Split split) {
        String subject = "Split Bill · " + billTitle(restaurant);
        StringBuilder message = new StringBuilder(subject)
                .append("\nReceipt total: ").append(BillMath.rm(split.grandTotal))
                .append("\nItems: ").append(BillMath.rm(split.subtotal))
                .append("\nSST: ").append(BillMath.rm(split.taxTotal))
                .append("\nService charge: ").append(BillMath.rm(split.serviceTotal));
        if (split.adjustmentTotal != 0) {
            message.append("\nAdjustment: ").append(BillMath.rm(split.adjustmentTotal));
        }
        message.append("\n\nShares\n");
        for (int i = 0; i < diners.size(); i++) {
            message.append("• ").append(dinerName(i)).append(": ").append(BillMath.rm(split.total[i]));
            if (i == 0) {
                message.append(" (bill payer)");
            } else {
                long balance = split.total[i] - diners.get(i).received;
                if (balance > 0) message.append(" · owes ").append(BillMath.rm(balance));
                else if (balance < 0) message.append(" · refund ").append(BillMath.rm(-balance));
                else if (split.total[i] > 0) message.append(" · paid");
                if (balance > 0 && diners.get(i).received > 0) {
                    message.append(" (").append(BillMath.rm(diners.get(i).received)).append(" received)");
                }
            }
            message.append('\n');
        }
        message.append("\nStill to collect: ").append(BillMath.rm(outstanding(split)));
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, message.toString()), "Share bill result"));
    }

}
