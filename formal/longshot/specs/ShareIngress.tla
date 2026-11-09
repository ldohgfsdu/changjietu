------------------------------ MODULE ShareIngress ------------------------------
EXTENDS Naturals, FiniteSets

CONSTANT MaxGeneration

VARIABLES generation, inFlight, displayed, shareSeq, pending

vars == <<generation, inFlight, displayed, shareSeq, pending>>

Init ==
    /\ generation = 0
    /\ inFlight = {}
    /\ displayed = 0
    /\ shareSeq = 0
    /\ pending = 0

OpenPicker ==
    /\ generation < MaxGeneration
    /\ generation' = generation + 1
    /\ inFlight' = inFlight \cup {generation + 1}
    /\ displayed' = 0
    /\ UNCHANGED <<shareSeq, pending>>

OpenShare ==
    /\ generation < MaxGeneration
    /\ shareSeq < MaxGeneration
    /\ generation' = generation + 1
    /\ inFlight' = inFlight \cup {generation + 1}
    /\ displayed' = 0
    /\ shareSeq' = shareSeq + 1
    /\ pending' = shareSeq + 1

Complete(g) ==
    /\ g \in inFlight
    /\ inFlight' = inFlight \ {g}
    /\ displayed' = IF g = generation THEN g ELSE displayed
    /\ UNCHANGED <<generation, shareSeq, pending>>

Ack(id) ==
    /\ pending = id
    /\ pending # 0
    /\ pending' = 0
    /\ UNCHANGED <<generation, inFlight, displayed, shareSeq>>

Stop ==
    /\ generation = MaxGeneration
    /\ inFlight = {}
    /\ pending = 0
    /\ UNCHANGED vars

Next ==
    \/ OpenPicker
    \/ OpenShare
    \/ \E g \in inFlight : Complete(g)
    \/ \E id \in 1..MaxGeneration : Ack(id)
    \/ Stop

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ generation \in 0..MaxGeneration
    /\ inFlight \subseteq 1..MaxGeneration
    /\ displayed \in 0..MaxGeneration
    /\ shareSeq \in 0..MaxGeneration
    /\ pending \in 0..MaxGeneration

NoStaleSourceDisplayed == displayed = 0 \/ displayed = generation

PendingBelongsToConsumedShare == pending = 0 \/ pending <= shareSeq
=============================================================================