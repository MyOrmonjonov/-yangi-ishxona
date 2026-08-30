package org.example.yangi_ishxona.bot;

import org.example.yangi_ishxona.service.NotifyButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

final class Keyboards {

    private Keyboards() {
    }

    static InlineKeyboardMarkup fromRows(List<List<NotifyButton>> rows) {
        var builder = InlineKeyboardMarkup.builder();
        for (List<NotifyButton> row : rows) {
            InlineKeyboardRow kbRow = new InlineKeyboardRow();
            for (NotifyButton button : row) {
                kbRow.add(InlineKeyboardButton.builder()
                        .text(button.label())
                        .callbackData(button.callbackData())
                        .build());
            }
            builder.keyboardRow(kbRow);
        }
        return builder.build();
    }

    static InlineKeyboardMarkup singleColumn(List<NotifyButton> buttons) {
        return fromRows(buttons.stream().map(List::of).toList());
    }
}
