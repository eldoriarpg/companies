package de.eldoria.companies.configuration.elements.companysettings;

public class CompanyTaxes {
    private double percent = 0.1;

    /**
     * Returns the amount after subtracting the taxes
     *
     * @param amount amount
     * @return amount after taxes
     */
    public double take(double amount) {
        return (1 - percent) * amount;
    }

    public double percent() {
        return percent;
    }

    public void percent(double percent) {
        this.percent = percent;
    }
}
