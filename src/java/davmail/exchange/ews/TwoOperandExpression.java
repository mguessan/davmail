/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.exchange.ews;

import davmail.util.StringUtil;

/**
 * Two operand expression.
 */
public class TwoOperandExpression implements SearchExpression {
    @SuppressWarnings({"UnusedDeclaration"})
    protected enum Operator {
        IsEqualTo, IsNotEqualTo, IsGreaterThan, IsGreaterThanOrEqualTo, IsLessThan, IsLessThanOrEqualTo
    }

    protected final Operator operator;
    protected final FieldURI fieldURI;
    protected final String value;

    /**
     * Create two operand expression.
     *
     * @param operator operator
     * @param fieldURI field operand
     * @param value    value operand
     */
    public TwoOperandExpression(Operator operator, FieldURI fieldURI, String value) {
        this.operator = operator;
        this.fieldURI = fieldURI;
        this.value = value;
    }

    public void appendTo(StringBuilder buffer) {
        buffer.append("<t:").append(operator.toString()).append('>');
        fieldURI.appendTo(buffer);

        buffer.append("<t:FieldURIOrConstant><t:Constant Value=\"");
        // encode urlcompname
        if (fieldURI instanceof ExtendedFieldURI && "0x10f3".equals(((ExtendedFieldURI) fieldURI).propertyTag)) {
            buffer.append(StringUtil.xmlEncodeAttribute(StringUtil.encodeUrlcompname(value)));
        } else {
            buffer.append(StringUtil.xmlEncodeAttribute(value));
        }
        buffer.append("\"/></t:FieldURIOrConstant>");

        buffer.append("</t:").append(operator.toString()).append('>');
    }

}
