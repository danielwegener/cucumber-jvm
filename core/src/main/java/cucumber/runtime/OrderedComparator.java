package cucumber.runtime;

import java.util.Comparator;

class OrderedComparator implements Comparator<HasOrder> {
    private final boolean ascending;

    public OrderedComparator(boolean ascending) {
        this.ascending = ascending;
    }

    @Override
    public int compare(HasOrder hook1, HasOrder hook2) {
        int comparison = hook1.getOrder() - hook2.getOrder();
        return ascending ? comparison : -comparison;
    }
}
