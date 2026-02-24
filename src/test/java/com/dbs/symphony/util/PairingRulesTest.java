package com.dbs.symphony.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PairingRulesTest {

    @Test
    void isManagerGroup_true() {
        assertTrue(PairingRules.isManagerGroup("Finance_Managers"));
    }

    @Test
    void isManagerGroup_false() {
        assertFalse(PairingRules.isManagerGroup("Finance_Users"));
        assertFalse(PairingRules.isManagerGroup("Finance"));
        assertFalse(PairingRules.isManagerGroup(null));
    }

    @Test
    void toUserGroup_derivesCorrectly() {
        assertEquals("Finance_Users", PairingRules.toUserGroup("Finance_Managers").orElseThrow());
    }

    @Test
    void toUserGroup_emptyForNonManagerGroup() {
        assertTrue(PairingRules.toUserGroup("Finance_Users").isEmpty());
        assertTrue(PairingRules.toUserGroup("Finance").isEmpty());
        assertTrue(PairingRules.toUserGroup(null).isEmpty());
    }

    @Test
    void extractTeamName_worksForManagersAndUsers() {
        assertEquals("Finance", PairingRules.extractTeamName("Finance_Managers").orElseThrow());
        assertEquals("Finance", PairingRules.extractTeamName("Finance_Users").orElseThrow());
        assertTrue(PairingRules.extractTeamName("Finance").isEmpty());
    }
}