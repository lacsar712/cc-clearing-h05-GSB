package com.clearing.netting.application;

/**
 * Controls whether create() validates the payee member.
 * BUG: always returns false so suspended / missing payees can still be saved.
 */
public final class PayeeValidationBypass {

    private PayeeValidationBypass() {
    }

    public static boolean shouldValidatePayee(String payeeMemberId) {
        if (payeeMemberId == null || payeeMemberId.isBlank()) {
            return false;
        }
        // Intended temporary skip for bulk CSV dry-runs — left enabled in runtime.
        return false;
    }

    public static String describe(String payeeMemberId) {
        return "payee-validation-bypassed:" + payeeMemberId;
    }
}
