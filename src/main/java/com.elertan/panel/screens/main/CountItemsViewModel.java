package com.elertan.panel.screens.main;

import com.elertan.ItemLockService;
import com.elertan.ItemLockService.Checklist;
import com.elertan.ui.Property;
import com.elertan.utils.Subscription;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.NumberFormat;
import java.util.concurrent.CompletionException;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CountItemsViewModel implements AutoCloseable {

    public final Property<Checklist> checklist;
    public final Property<Boolean> isSubmitting = new Property<>(false);
    public final Property<String> errorMessage = new Property<>(null);
    private final ItemLockService itemLockService;
    private final Runnable navigateToConfig;
    private final Subscription checklistSubscription;

    private CountItemsViewModel(ItemLockService itemLockService, Runnable navigateToConfig) {
        this.itemLockService = itemLockService;
        this.navigateToConfig = navigateToConfig;
        checklist = new Property<>(itemLockService.getChecklist().get());
        checklistSubscription = itemLockService.getChecklist().subscribe(checklist::set);
    }

    @Override
    public void close() {
        checklistSubscription.dispose();
    }

    public void onSettingsClicked() {
        navigateToConfig.run();
    }

    public void onCountClicked() {
        Checklist current = checklist.get();
        if (current == null || !current.isComplete()) {
            return;
        }
        NumberFormat format = NumberFormat.getIntegerInstance();
        int result = JOptionPane.showConfirmDialog(
            null,
            "Your bank holds " + format.format(current.getBankItems()) + " different items"
                + " and " + format.format(current.getCoins()) + " coins.\n\n"
                + "All of it gets locked and stays in your bank.\n"
                + "Only what you get from now on counts.",
            "Count your items",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        isSubmitting.set(true);
        errorMessage.set(null);
        itemLockService.confirmCount().whenComplete((__, throwable) -> {
            if (throwable != null) {
                fail(throwable);
                return;
            }
            isSubmitting.set(false);
        });
    }

    private void fail(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
        log.error("Failed to count starting items", cause);
        String message = cause instanceof IllegalStateException
            ? cause.getMessage()
            : "Your items could not be saved. Please try again.";
        errorMessage.set(message);
        isSubmitting.set(false);
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        CountItemsViewModel create(Runnable navigateToConfig);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private ItemLockService itemLockService;

        @Override
        public CountItemsViewModel create(Runnable navigateToConfig) {
            return new CountItemsViewModel(itemLockService, navigateToConfig);
        }
    }
}
