import java.util.Map;

/**
 * The BM25 retrieval model has no parameters.
 *
 * @author Jiachen Li (AndrewID: jiachenl)
 */

public class RetrievalModelBM25 extends RetrievalModel {

    // BM25 retrieval parameters
    public double k_1 = 0.0;
    public double b = 0.0;
    public double k_3 = 0.0;

    /**
     * Constructor for RetrievalModelBM25
     * 
     * @param  params A map that contains the parameter/value pairs
     */
    public RetrievalModelBM25(Map<String, String> params) {
        // TODO Auto-generated constructor stub
        if (!params.containsKey("BM25:k_1") || !params.containsKey("BM25:b")
                || !params.containsKey("BM25:k_3")) {
            System.err.println("Error: Cannot find the required parameters!");
            System.exit(1);
        }
        // set k_1
        double val = Double.parseDouble(params.get("BM25:k_1"));
        if (val >= 0.0)
            this.k_1 = val;
        else {
            System.err.println("Error: Wrong parameter value for k_1");
            System.exit(1);
        }

        // set b
        val = Double.parseDouble(params.get("BM25:b"));
        if (val >= 0.0 && val <= 1.0)
            this.b = val;
        else {
            System.err.println("Error: Wrong parameter value for b");
            System.exit(1);
        }
        // set k_3
        val = Double.parseDouble(params.get("BM25:k_3"));
        if (val >= 0.0)
            this.k_3 = val;
        else {
            System.err.println("Error: Wrong parameter value for k_3");
            System.exit(1);
        }
    }

    /**
     * Set parameters (i.e., k_1, b, k_3) for retrieval model BM25.
     * 
     * Please use this method to set parameters as it contains value check.
     * 
     * @param parameterName
     *            name of the parameters, e.g. k_1, b, k_3
     * @param value
     *            value of parameter to set
     */
    @Override
    public boolean setParameter(String parameterName, double value) {
        if (parameterName.equals("BM25:k_1") && value >= 0.0) {
            k_1 = value;
        } else if (parameterName.equals("BM25:b") && value <= 1.0
                && value >= 0.0) {
            b = value;
        } else if (parameterName.equals("BM25:k_3") && value >= 0.0) {
            k_3 = value;
        } else {
            System.err
                    .println("Error: Unknown parameter name or wrong parameter value "
                            + "for retrieval model BM25: "
                            + parameterName
                            + "=" + value);
            return false;
        }
        return true;
    }

    @Override
    public boolean setParameter(String parameterName, String value_s) {
        double value = Double.parseDouble(value_s);

        if (parameterName.equals("BM25:k_1") && value >= 0.0) {
            k_1 = value;
        } else if (parameterName.equals("BM25:b") && value <= 1.0
                && value >= 0.0) {
            b = value;
        } else if (parameterName.equals("BM25:k_3") && value >= 0.0) {
            k_3 = value;
        } else {
            System.err
                    .println("Error: Unknown parameter name or wrong parameter value "
                            + "for retrieval model BM25: "
                            + parameterName
                            + "=" + value);
            return false;
        }
        return true;
    }

}
