package generator;

import configuration.DataConfigEntry;
import configuration.ProcessorConfigEntry;
import graql.lang.Graql;
import graql.lang.pattern.Pattern;
import graql.lang.pattern.variable.ThingVariable;
import graql.lang.pattern.variable.ThingVariable.Attribute;
import graql.lang.pattern.variable.UnboundVariable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;

import static generator.GeneratorUtil.addValue;
import static generator.GeneratorUtil.malformedRow;

public class AttributeInsertGenerator extends InsertGenerator {

    private static final Logger appLogger = LogManager.getLogger("com.bayer.dt.grami");
    private static final Logger dataLogger = LogManager.getLogger("com.bayer.dt.grami.data");
    private final DataConfigEntry dce;
    private final ProcessorConfigEntry pce;

    public AttributeInsertGenerator(DataConfigEntry dataConfigEntry, ProcessorConfigEntry processorConfigEntry) {
        super();
        this.dce = dataConfigEntry;
        this.pce = processorConfigEntry;
        appLogger.debug("Creating AttributeInsertGenerator for processor " + processorConfigEntry.getProcessor() + " of type " + processorConfigEntry.getProcessorType());
    }

    public ArrayList<ThingVariable<?>> graknAttributeInsert(ArrayList<String> rows,
                                                            String header, int rowCounter) throws IllegalArgumentException {
        ArrayList<ThingVariable<?>> patterns = new ArrayList<>();
        int batchCount = 1;
        for (String row : rows) {
            try {
                ThingVariable<?> temp = graknAttributeQueryFromRow(row, header, rowCounter + batchCount);
                if (temp != null) {
                    patterns.add(temp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            batchCount = batchCount + 1;
        }
        return patterns;
    }

    public ThingVariable<Attribute> graknAttributeQueryFromRow(String row,
                                                               String header, int rowCounter) throws Exception {
        String fileSeparator = dce.getSeparator();
        String[] rowTokens = row.split(fileSeparator);
        String[] columnNames = header.split(fileSeparator);
        appLogger.debug("processing tokenized row: " + Arrays.toString(rowTokens));
        malformedRow(row, rowTokens, columnNames.length);

        UnboundVariable attributeInitialStatement = addAttributeToStatement();
        Attribute attributeInsertStatement = null;

        for (DataConfigEntry.DataConfigGeneratorMapping generatorMappingForAttribute : dce.getAttributes()) {
            attributeInsertStatement = addValue(rowTokens, attributeInitialStatement, rowCounter, columnNames, generatorMappingForAttribute, pce, generatorMappingForAttribute.getPreprocessor());
        }

        if (attributeInsertStatement != null) {
            attributeInsertStatement = attributeInsertStatement.isa(pce.getSchemaType());

            if (isValid(attributeInsertStatement)) {
                appLogger.debug("valid query: <insert " + attributeInsertStatement.toString() + ";>");
                return attributeInsertStatement;
            } else {
                dataLogger.warn("in datapath <" + dce.getDataPath() + ">: skipped row " + rowCounter + " b/c does not have a proper <isa> statement or is missing required attributes. Faulty tokenized row: " + Arrays.toString(rowTokens));
                return null;
            }
        } else {
            return null;
        }
    }

    private UnboundVariable addAttributeToStatement() {
        if (pce.getSchemaType() != null) {
            return Graql.var("a");
        } else {
            throw new IllegalArgumentException("Required field <schemaType> not set in processor " + pce.getProcessor());
        }
    }

    private boolean isValid(Pattern pa) {
        String patternAsString = pa.toString();
        return patternAsString.contains("isa " + pce.getSchemaType());
    }
}
