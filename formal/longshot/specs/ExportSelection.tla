------------------------------ MODULE ExportSelection ------------------------------
EXTENDS Naturals, FiniteSets

CONSTANT MaxExports

Rows == {1, 2, 3}

VARIABLES selected, phase, snapshot, delivered, exportCount

vars == <<selected, phase, snapshot, delivered, exportCount>>

Init ==
    /\ selected = Rows
    /\ phase = "ready"
    /\ snapshot = {}
    /\ delivered = {}
    /\ exportCount = 0

Toggle(i) ==
    /\ i \in Rows
    /\ selected' = IF i \in selected THEN selected \ {i} ELSE selected \cup {i}
    /\ UNCHANGED <<phase, snapshot, delivered, exportCount>>

StartExport ==
    /\ phase = "ready"
    /\ selected # {}
    /\ exportCount < MaxExports
    /\ snapshot' = selected
    /\ delivered' = {}
    /\ exportCount' = exportCount + 1
    /\ phase' = "exporting"

FinishExport ==
    /\ phase = "exporting"
    /\ delivered' = snapshot
    /\ phase' = "ready"
    /\ UNCHANGED <<selected, snapshot, exportCount>>

FailExport ==
    /\ phase = "exporting"
    /\ delivered' = {}
    /\ phase' = "ready"
    /\ UNCHANGED <<selected, snapshot, exportCount>>

Stop ==
    /\ exportCount = MaxExports
    /\ phase = "ready"
    /\ UNCHANGED vars

Next ==
    \/ \E i \in Rows : Toggle(i)
    \/ StartExport
    \/ FinishExport
    \/ FailExport
    \/ Stop

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ selected \subseteq Rows
    /\ snapshot \subseteq Rows
    /\ delivered \subseteq Rows
    /\ exportCount \in 0..MaxExports
    /\ phase \in {"ready", "exporting"}

NoEmptySubmission == phase # "exporting" \/ snapshot # {}

OnlySubmittedSlicesDelivered == delivered \subseteq snapshot
=============================================================================