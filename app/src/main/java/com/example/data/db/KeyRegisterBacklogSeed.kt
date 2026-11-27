package com.example.data.db

/**
 * Verified backlog rows extracted from:
 * "WSF-PMD-F-07 - Key- Access card Register - Iss 00 - 07-10-2024 UPDATED TO 26TH.pdf"
 *
 * Includes:
 * - SI 2..440 (Pages 1–11; divider SI 83 excluded)
 * - SI 1283 (Page 32 red-flagged duplication row)
 * - Unnumbered Move-out row after SI 1567 (Page 38, stored at unused divider slot SI = 1)
 * - All active/unreturned Nov 26 red-flagged rows (SI 1634, 1638, 1639, 1643, 1644, 1645)
 *
 * Status mapping strictly keeps `KeyStatus.isUnreturnedOrActive(status) == true` ONLY for the
 * 7 red-highlighted rows (SI 1283, 1634, 1638, 1639, 1643, 1644, 1645). Settled tenant
 * Move-in / Transfer rows with confirmed T-codes use `KeyStatus.ISSUED_TO_TENANT`.
 */
object KeyRegisterBacklogSeed {

    private val RED_NOT_YET_RETURNED_SIS = setOf(1283, 1634, 1643)
    private val RED_ACTIVE_ISSUED_SIS = setOf(1638, 1639, 1644, 1645)
    private val WORK_ORDER_REGEX = Regex("""(?:work order(?:\s*no\.?)?\s*#?\s*|#)(\d{5,})""", RegexOption.IGNORE_CASE)

    fun buildKeyRegisterBacklog(): List<KeyRegisterEntity> {
        val raw = mutableListOf<KeyRegisterEntity>()
        parseChunk(CHUNK_1, raw)
        parseChunk(CHUNK_2, raw)
        parseChunk(CHUNK_3, raw)
        parseChunk(CHUNK_TAIL, raw)
        return raw.sortedByDescending { it.si }
    }

    private fun parseChunk(chunk: String, out: MutableList<KeyRegisterEntity>) {
        chunk.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val parts = line.split('|')
                if (parts.size < 11) return@forEach
                val si = parts[0].trim().toIntOrNull() ?: return@forEach
                val dateOut = parts[1].trim()
                val timeOut = parts[2].trim()
                val unit = parts[3].trim()
                val keyType = parts[4].trim()
                val purpose = parts[5].trim()
                val issuedByRaw = parts[6].trim().let { if (it == "-") "" else it }
                val issuedToRaw = parts[7].trim()
                val dateIn = parts[8].trim().let { if (it == "-") "" else it }
                val timeIn = parts[9].trim().let { if (it == "-") "" else it }
                val comments = parts[10].trim()

                val isPendingTCode = issuedToRaw.contains("pending", ignoreCase = true) ||
                    comments.contains("pending", ignoreCase = true)
                val isMoveInOrTransfer = purpose.contains("move-in", ignoreCase = true) ||
                    purpose.contains("move in", ignoreCase = true) ||
                    purpose.equals("transfer", ignoreCase = true)
                val isNotYetReturned = !isMoveInOrTransfer && (
                    si in RED_NOT_YET_RETURNED_SIS ||
                        comments.contains("not yet returned", ignoreCase = true)
                    )
                val isActiveIssued = !isMoveInOrTransfer && isPendingTCode
                val hasReturnTimestamp = dateIn.isNotBlank() || timeIn.isNotBlank()
                val isReturnedComment = comments.contains("returned", ignoreCase = true) ||
                    comments.contains("handed over to pm", ignoreCase = true) ||
                    comments.contains("cylinder fixed", ignoreCase = true)
                val isAlreadyWithWaseef = comments.contains("with waseef care team", ignoreCase = true)

                val status = when {
                    isNotYetReturned -> KeyStatus.NOT_YET_RETURNED
                    isMoveInOrTransfer && isPendingTCode -> KeyStatus.MOVE_IN_PENDING_TCODE
                    isActiveIssued -> KeyStatus.ISSUED
                    hasReturnTimestamp || isReturnedComment -> KeyStatus.RETURNED
                    isMoveInOrTransfer ||
                        comments.contains("issued to tenant", ignoreCase = true) ||
                        isAlreadyWithWaseef -> KeyStatus.ISSUED_TO_TENANT
                    else -> KeyStatus.ISSUED
                }

                val woMatch = WORK_ORDER_REGEX.find(purpose)?.groupValues?.getOrNull(1)
                val issuedToDisplay: String
                val qidOrNote: String
                when {
                    issuedToRaw.startsWith("T-", ignoreCase = true) -> {
                        qidOrNote = issuedToRaw
                        issuedToDisplay = if (issuedByRaw.isNotBlank()) issuedByRaw else issuedToRaw
                    }
                    issuedToRaw.contains("pending", ignoreCase = true) -> {
                        qidOrNote = if (issuedByRaw.isNotBlank()) {
                            "T-code follow-up required from $issuedByRaw"
                        } else {
                            issuedToRaw
                        }
                        issuedToDisplay = issuedToRaw
                    }
                    issuedByRaw.startsWith("T-", ignoreCase = true) -> {
                        qidOrNote = issuedByRaw
                        issuedToDisplay = issuedToRaw
                    }
                    else -> {
                        val noteParts = mutableListOf<String>()
                        if (!woMatch.isNullOrBlank()) noteParts.add("WO #$woMatch")
                        if (issuedByRaw.isNotBlank() && issuedByRaw != issuedToRaw) {
                            noteParts.add("Physical Issuer: $issuedByRaw")
                        }
                        qidOrNote = noteParts.joinToString(" • ")
                        issuedToDisplay = issuedToRaw
                    }
                }

                val returnedBy = if (status == KeyStatus.RETURNED) issuedToDisplay else ""

                out.add(
                    KeyRegisterEntity(
                        si = si,
                        dateOut = dateOut,
                        timeOut = timeOut,
                        flatUnitLocation = unit,
                        keyCardType = keyType,
                        purpose = purpose,
                        issuedTo = issuedToDisplay,
                        qidOrNote = qidOrNote,
                        dateIn = dateIn,
                        timeIn = timeIn,
                        returnedBy = returnedBy,
                        remarks = comments,
                        status = status,
                        issuedBy = issuedByRaw
                    )
                )
            }
    }

    private const val CHUNK_1 = """
2|11/23/2024|03:04PM|M-A01-25|Key|Duplication|6562|Tenant(M-A01-25)|11/24/2024|2:00PM|Key returned (EXTRA COPY auth MR. SHAJITH (PM))
3|11/25/2024|08:00AM|M-A06-18|Key|Refurbishment|769|5637|11/25/2024|8:07AM|Key returned
4|11/25/2024|9:20AM|M-C16-47|Key|Opening main door for a tenant refer to work order|889|851|11/25/2024|1:07PM|Key returned
5|11/26/2024|10:50AM|M-A06-18|Key|Refurbishment|769|5872|11/28/2024|10:28AM|Key returned
6|11/28/2024|8:00AM|M-F29-38|Key|Refurbishment|769|5637|12/7/2024|5:41PM|Key returned
7|11/28/2024|3:49PM|M-C11-28|Key|FAFF Inspection|889|0787|11/28/2024|3:58PM|Key returned
8|12/1/2024|10:55AM|M-A01-31|Key|View|769|769|12/1/2024|10:59AM|Key returned
9|-|-|M-F29-26|Key|Move-out|-|6562|12/1/2024|3:30PM|3 Keys handed over to PM
10|12/1/2024|-|M-D17-16|Key|Refurbishment|6562|5872|12/1/2024|4:46PM|Key returned
11|12/2/2024|7:50AM|M-F29-26|Key|Checking apartment keys|769|4257|12/2/2024|8:09AM|Key returned
12|12/2/2024|8:14AM|M-D17-16|Key|Refurbishment|769|5649|12/3/2024|8:30AM|Key returned
13|12/2/2024|-|M-C13-18|Key|Move-out|-|6562|12/2/2024|12:40PM|Key returned
14|12/3/2024|1:17PM|M-D17-16|Key|Move-in|769|4257|-|-|Key issued to tenant
15|12/3/2024|2:50PM|M-C15-48|Key|Key cylinder swap|769|4777|12/3/2024|3:30:00PM|Key returned
16|12/3/2024|2:50PM|M-D17-16|Key|Key cylinder swap|769|4777|12/3/2024|3:30:00PM|Key returned
17|-|-|M-D19-43|Key|Refurbishment|6562|5637|12/3/2024|5:00PM|Key returned
18|12/4/2024|8:20AM|M-D19-43|Key|Cleaning by OTS team|769|4428|12/6/2024|11:00AM|Key returned
19|12/4/2024|12:00PM|M-F29-48|Key|View|769|769|12/4/2024|12:30PM|Key returned
20|12/4/2024|1:30PM|M-F29-26|Key|Refurbishment|6562|5637|12/12/2024|4:34PM|Key returned
21|12/4/2024|1:30PM|M-C13-18|Key|Refurbishment|6562|5637|12/17/2024|7:36AM|Key returned
22|12/5/2024|9:45AM|M-F30-17|Key|Request for access- Ref work order no. 2534543|769|451|12/5/2024|10:00AM|Key returned
23|12/5/2024|1:41PM|M-E24-11|2 Keys|Refurbishment|6562|Tenant|12/8/2024|8:16PM|2 Keys returned
24|-|-|M-C15-48|Key|Move-out|-|6562|12/5/2024|10:30AM|Key returned
25|12/5/2024|7:50PM|M-C15-48|Key|Refurbishment|395|Tenant|12/8/2024|7:35PM|Key returned
26|12/6/2024|8:24AM|M-F29-38|Key|Cleaning by OTS team|889|5658|-|-|Key not available, with waseef care team already
27|12/7/2024|9:26AM|M-A01-31|Key|FAFF Inspection|889|0788|12/7/2024|12:45PM|Key returned
28|12/7/2024|2:44PM|Shop no.3 inside club|5 Keys|Checking furniture|769|1316|12/7/2024|03:28PM|Keys returned
29|12/8/2024|8:11AM|M-F29-38|Key|Cleaning by OTS team|889|4428|12/9/2024|4:49PM|Key returned
30|12/8/2024|11:24AM|Shop no.3 inside club|5 Keys|Checking furniture|889|1316|12/8/2024|11:57AM|Keys returned
31|12/9/2024|10:48AM|M-E24-11|2 Keys|Inspection by PM|769|6562|12/9/2024|11:46AM|Keys returned
32|12/9/2024|12:50PM|M-F29-38|Key|View|769|769|12/9/2024|1:12PM|Key returned, returned to OTS team
33|12/9/2024|2:35PM|M-C15-48|Key|Refurbishment|769|5637|12/9/2024|3:23PM|Key returned
34|12/9/2024|1:12PM|M-F29-38|Key|Cleaning by OTS team|769|5658|12/9/2024|4:41PM|Key returned
35|12/10/2024|10:00AM|M-C13-18|Key|View|769|769|12/10/2024|10:27AM|Key returned
36|12/10/2024|10:00AM|M-D20-35|Key|View|769|769|12/10/2024|10:27AM|Key returned
37|12/10/2024|11:20AM|M-D19-43|Key|View|769|769|12/10/2024|11:41AM|Key returned
38|12/10/2024|1:41PM|M-E24-11|Key|Refurbishment|769|5637|12/16/2024|2:36PM|Key returned
39|12/10/2024|5:09PM|M-E26-28|Key|Refurbishment|769|5637|12/11/2024|4:17PM|Key returned
40|12/11/2024|1:30PM|M-D19-43|Key|View|769|769|12/11/2024|1:50PM|Key returned
41|12/13/2024|8:06AM|M-F29-26|Key|Cleaning by OTS team|769|5658|12/14/2024|1:01PM|Key returned
42|12/14/2024|1:40PM|M-D19-43|Key|View|769|769|12/14/2024|2:07PM|Key returned
43|12/14/2024|2:50PM|M-F29-26|Key|Refurbishment|769|5637|12/15/2024|7:58AM|Key returned
44|12/14/2024|4:37PM|M-D19-43|Key|Refurbishment|769|5637|12/17/2024|7:36AM|Key returned
45|12/15/2024|8:10AM|M-D20-35|Key|Refurbishment|769|6465|12/26/2024|11:30AM|Key returned
46|12/15/2024|11:50AM|M-A01-31|Key|View|769|769|12/15/2024|12:13PM|Key returned
47|12/16/2024|9:40AM|M-F29-26|Key|View|769|769|12/16/2024|9:55AM|Key returned
48|12/16/2024|9:40AM|M-C15-48|Key|View|769|769|12/16/2024|9:55AM|Key returned
49|12/17/2024|8:09AM|M-C13-18|Key|Cleaning by OTS team|769|5658|12/17/2024|5:00PM|Key returned
50|12/17/2024|11:07AM|M-E24-11|Key|View|769|769|12/17/2024|11:31AM|Key returned
51|12/17/2024|1:41PM|M-E25-33|Key|Request for access- Ref work order no. 2559607|769|889|12/17/2024|2:00PM|Key returned
52|12/17/2024|-|M-C15-22|2 Keys|Move-out|-|6562|12/17/2024|3:15PM|2 Keys returned
53|12/18/2024|8:11AM|M-E24-11|Key|Cleaning by OTS team|769|4428|12/20/2024|4:45PM|Key returned
54|12/21/2024|9:07AM|M-F29-38|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
55|12/21/2024|9:07AM|M-D19-43|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
56|12/21/2024|9:07AM|M-F29-48|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
57|12/21/2024|9:07AM|M-D21-41|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
58|12/21/2024|9:07AM|M-E26-28|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
59|12/21/2024|9:07AM|M-F29-26|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
60|12/21/2024|9:07AM|M-E24-11|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
61|12/21/2024|9:07AM|M-D20-35|Key|FAFF Inspection|769|0788|12/21/2024|11:30AM|Key returned
62|12/21/2024|9:35AM|M-E24-11|Key|Refurbishment|769|5637|12/22/2024|10:55AM|Key returned
63|12/21/2024|8:48AM|M-A01-31|Key|Taking pictures by OTS team|769|5658|12/21/2024|10:41AM|Key returned
64|12/21/2024|2:30PM|M-A01-31|2 Keys|Move-in|6562|4257|-|-|2 Keys issued to tenant
65|12/22/2024|10:00AM|M-D21-41|Key|View|769|769|12/22/2024|10:21AM|Key returned
66|12/23/2024|9:50AM|M-D21-41|Key|View|769|769|12/23/2024|10:16AM|Key returned
67|12/23/2024|12:07PM|M-E24-11|Key|View|889|889|12/23/2024|12:47PM|Key returned
68|12/23/2024|12:16PM|M-C15-22|Key|View|769|769|12/23/2024|12:24PM|Key returned
69|12/23/2024|1:21PM|M-C13-11|Key|View|769|769|12/23/2024|1:33PM|Key returned
70|12/23/2024|6:20PM|M-C15-22|Key|View|395|395|12/23/2024|6:45PM|Key returned
71|12/24/2024|12:17PM|M-F29-48|Key|View|769|769|12/24/2024|12:27PM|Key returned
72|12/25/2024|8:31AM|M-C15-22|Key|Refurbishment|769|5637|1/4/2025|7:44AM|Key returned
73|12/25/2024|9:35AM|M-C13-11|3 Keys|Move-In|769|4257|-|-|3 Keys issued to tenant
74|12/25/2024|3:18PM|Club house|ALL|FAFF Inspection|889|889|12/25/2024|3:56PM|All Keys returned
75|12/25/2024|1:39PM|Shop 1 near club house|Key|FAFF Inspection|889|889|12/25/2024|2:07PM|Key returned
76|-|-|M-A04-25|2 Keys|Move-out|-|6562|12/29/2024|3:15PM|2 Keys returned
77|12/29/2024|10:18AM|M-C13-18|Key|CCTV Mock-up|6562|4368|12/31/2024|9:00AM|Key returned
78|12/29/2024|12:20PM|M-E24-11|2 Keys|Move-in|6562|4257|-|-|2 Keys issued to tenant
79|12/30/2024|8:33AM|M-C15-48|Key|Refurbishment|769|5637|1/4/2025|11:46AM|Key returned
80|12/30/2024|12:55PM|M-D21-41|Key|View|769|769|12/30/2024|1:13PM|Key returned
81|-|-|M-A01-12|3 Keys|Move-out|-|6562|12/30/2024|2:00PM|3 Keys returned
82|12/31/2024|10:55AM|M-F29-26|2 Keys|Move-in|6562|4257|-|-|2 Keys issued to tenant
84|1/1/2025|9:20AM|M-F29-38|Key|Cleaning by OTS team|769|4428|1/1/2025|1:10PM|Key returned
85|1/1/2025|9:31AM|M-A01-12|Key|Refurbishment|769|5637|1/15/2025|9:57AM|Key returned
86|1/1/2025|9:31AM|M-A04-25|Key|Refurbishment|769|5637|1/14/2025|7:40AM|Key returned
87|1/1/2025|11:18AM|M-D19-32|Key|Opening main door for a tenant refer to work order|769|769|1/1/2025|11:40AM|Key returned
88|1/1/2025|1:30PM|M-C11-28|2 Keys|Move-in|6562|4257|-|-|2 Keys issued to tenant
89|-|-|M-F27-27|2 Keys|Move-out|-|6562|1/2/2025|11:15AM|2 Keys returned by tenant
90|1/1/2025|2:50PM|M-D17-26|2 Keys|Move-in|6562|T-0057122|-|-|2 Keys issued to tenant
91|1/2/2025|12:37PM|M-A04-25|Key|View|769|769|1/2/2025|12:58PM|Key returned
92|1/2/2025|1:26PM|M-D21-41|Key|View|769|769|1/2/2025|1:37PM|Key returned
93|1/2/2025|2:15PM|M-A04-27|2 Keys|Move-in|6562|T-0057241|-|-|2 Keys issued to tenant
94|1/4/2025|8:28AM|M-C15-22|Key|Cleaning by OTS team|769|5658|1/8/2025|4:35PM|Key returned
95|1/4/2025|12:44PM|M-A04-25|Key|View|769|769|1/4/2025|1:04PM|Key returned
96|1/5/2025|7:31AM|M-D21-41|Key|AC removal, Gasco work|769|774|1/5/2025|7:57AM|Key returned
97|1/5/2025|3:34PM|M-C15-22|Key|Taking pictures by Waseef care team|769|5637|1/5/2025|6:00PM|Key returned
98|1/5/2025|3:34PM|M-C15-48|Key|Taking pictures by Waseef care team|769|5637|1/5/2025|6:00PM|Key returned
99|1/7/2025|8:34AM|M-D20-35|Key|Cleaning by OTS team|769|5658|1/10/2025|4:45PM|Key returned
100|1/7/2025|10:57AM|M-C13-18|Key|Waseef Inspection|769|769|1/7/2025|11:47AM|Key returned
101|1/7/2025|10:57AM|M-C15-48|Key|Waseef Inspection|769|769|1/7/2025|11:47AM|Key returned
102|1/7/2025|10:57AM|M-D20-35|Key|Waseef Inspection|769|769|1/7/2025|11:47AM|Key returned
103|1/10/2025|7:15AM|M-F27-27|Key|Refurbishment|769|5637|1/19/2025|11:13AM|Key returned
104|1/11/2025|11:30AM|M-E26-28|3 Keys|View|6562|5637|1/11/2025|12:00PM|Key returned
105|1/11/2025|1:24PM|M-D21-41|Key|View|769|769|1/11/2025|1:31PM|Key returned
106|1/11/2025|2:47PM|M-E25-37|2 Keys|Removal of AC, gas pipe replacement|889|4777|1/11/2025|3:12PM|Keys returned
107|1/11/2025|3:57PM|M-D20-35|Key|Refurbishment|769|6465|1/16/2025|11:45AM|Key returned
108|1/12/2025|1:14PM|M-D20-35|Key|View|769|769|1/12/2025|1:37PM|Key returned
109|1/12/2025|1:14PM|M-C15-22|Key|View|769|769|1/12/2025|1:37PM|Key returned
110|1/13/2025|2:55PM|M-A04-25|Key|View|769|769|1/13/2025|3:13PM|Key returned
111|1/14/2025|1:10PM|M-A04-25|Key|Cleaning by OTS team|769|5658|1/18/2025|10:46AM|Key returned
112|1/15/2025|4:30PM|M-A03-27|Key|Opening main door for a tenant refer to work order|769|769|1/15/2025|1/15/2025|Key returned
113|1/16/2025|11:11AM|M-C15-22|Key|View|769|769|1/16/2025|11:20 AM|Key returned
114|1/16/2025|1:19AM|M-A01-12|Key|View|769|769|1/16/2025|1:47PM|Key returned
115|1/16/2025|1:19AM|M-C15-22|Key|View|769|769|1/16/2025|1:47PM|Key returned
116|1/18/2025|8:30AM|M-A01-12|Key|Cleaning by OTS team|6562|5658|1/20/2025|4:39PM|Key returned
117|1/18/2025|11:58AM|M-A04-25|Key|View|769|769|1/18/2025|12:27PM|Key returned
118|1/19/2025|11:10AM|M-C15-22|Key|View|889|889|1/19/2025|11:26AM|Key returned
119|1/19/2025|1:08PM|M-D19-43|Key|Refurbishment|769|5637|1/20/2025|10:33AM|Key returned
120|1/20/2025|8:24AM|M-F27-27|Key|Cleaning by OTS team|769|5658|1/23/2025|4:19PM|Key returned
121|1/22/2025|2:25PM|M-A01-12|Key|View|769|769|1/22/2025|2:54PM|Key returned
122|1/23/2025|11:39AM|M-D20-35|Key|View|889|889|1/23/2025|12:00PM|Key returned
123|1/23/2025|2:19PM|M-C15-48|Key|View|889|889|1/23/2025|2:48PM|Key returned
124|1/23/2025|2:54PM|M-A04-25|5 Keys|View|889|4695|1/23/2025|5:23PM|5 Keys returned
125|1/23/2025|6:11PM|M-D19-43|Key|Shifting from M-B07-47 to M-D19-43|889|889|1/23/2025|11:15PM|Key returned
126|1/24/2025|11:55AM|M-F29-48|Key|Cleaning flat by tenant|769|769|1/24/2025|3:08PM|Key returned
127|1/24/2025|4:01PM|M-D19-43|Key|Shifting from M-B07-47 to M-D19-43|769|769|1/24/2025|10:30PM|Key returned
128|1/25/2025|10:11AM|M-A04-25|Key|Shifting from M-D20-28 to M-A04-25|769|769|1/29/2025|6:00AM|Key returned
129|1/26/2025|7:45AM|M-D20-35|Key|Refurbishment|769|4777|1/26/2025|1:48PM|Key returned
130|1/26/2025|1:12PM|M-D19-43|Key|Shifting from M-B07-47 to M-D19-43|769|769|1/26/2025|9:45PM|Key returned
131|1/27/2025|7:50AM|M-F27-27|Key|PM Inspection|6562|4695|1/27/2025|9:55AM|Key returned
132|1/27/2025|1:27PM|M-F29-38|Key|Fixing AC inside of flat|769|769|1/27/2025|3:58PM|Key returned
133|1/27/2025|3:25PM|M-A01-12|Key|Shifting household items into flat|769|769|1/27/2025|7:20PM|Key returned
134|1/27/2025|6:27PM|M-D19-43|Key|Shifting from M-B07-47 to M-D19-43|769|769|1/27/2025|7:43PM|Key returned
135|1/28/2025|5:07PM|M-C13-18|Key|Shifting from M-A03-18 to M-C13-18|769|769|1/28/2025|7:15PM|Key returned
136|1/28/2025|3:00PM|M-F27-27|3 Keys|Refurbishment|6562|5637|1/29/2025|7:30AM|3 Keys returned
137|1/28/2025|1:00PM|M-D21-41|2 Keys|Move in|6562|T-0057365|-|-|2 Keys issued
138|1/29/2025|8:29AM|M-D20-35|Key|Cleaning by OTS team|769|5658|1/29/2025|11:30AM|Key returned
139|1/29/2025|8:29AM|M-E26-28|Key|Cleaning by OTS team|769|5658|1/29/2025|11:30AM|Key returned
140|1/29/2025|8:29AM|M-D21-41|Key|Cleaning by OTS team|769|5658|1/29/2025|11:30AM|Key returned
141|-|-|M-B07-28|2 Keys|Move out|6562|T-0039386|1/29/2025|1:00PM|2 Keys returned
142|1/29/2025|2:00PM|M-E26-28|2 Keys|Move in|6562|T-0057193|-|-|2 Keys issued
143|1/29/2025|1:30PM|M-D19-43|2 Keys|Move in|6562|T-0056724|-|-|2 Keys issued
144|-|-|M-A05-34|2 Keys|Move out|6562|T-0045272|1/29/2025|1:15PM|2 Keys returned
145|1/29/2025|1:15PM|M-C13-18|1 Key|Move in|6562|T-0057392|-|-|1 Key issued
146|1/29/2025|2:30PM|M-F29-48|2 Keys|Move in|6562|T-0057309|-|-|2 Keys issued
147|1/29/2025|2:50PM|M-F27-27|2 Keys|Move in|6562|T-0057353|-|-|2 Keys issued
148|1/30/2025|2:15PM|M-F29-38|1 Key|Move in|6562|T-0057172|-|-|1 Key issued
149|1/30/2025|1:10PM|M-A04-25|1 Key|Move in|6562|T-0057366|-|-|1 Key issued
150|1/30/2025|1:20PM|M-A01-12|3 Keys|Move in|6562|T-0057517|-|-|3 Keys issued
151|-|-|M-D19-24|3 Keys|Move out|6562|T-005186|1/30/2025|2:00PM|3 Keys returned
152|-|-|M-B07-47|2 Keys|Move out|6562|T-0027392|1/30/2025|2:18PM|2 Keys returned
153|1/30/2025|10:25AM|M-A03-13|Key|Cleaning by OTS team|774|5658|1/30/2025|10:55AM|Key returned
154|2/1/2025|6:55AM|M-A05-34|Key|Refurbishment|769|5637|2/8/2025|1:18PM|Key returned
155|2/1/2025|6:55AM|M-B07-28|Key|Refurbishment|769|5637|2/4/2025|3:25PM|Key returned
156|2/3/2025|7:30AM|M-D19-24|Key|Refurbishment|6562|5637|2/10/2025|1:00PM|Key returned
157|2/3/2025|11:10AM|M-D20-35|Key|View|889|889|2/3/2025|11:43AM|Key returned
158|2/3/2025|-|M-C11-13|2 Keys|Move out|T-0042587|6562|2/3/2025|3:30PM|2 Keys returned
159|2/4/2025|7:30AM|M-A04-35|Key|Refurbishment|6562|5637|2/17/2025|12:12PM|Key returned
160|-|-|M-D20-28|2 Keys|Move out|T-0039948|6562|2/4/2025|12:30AM|2 Keys returned
"""

    private const val CHUNK_2 = """
161|2/7/2025|8:18AM|M-B07-28|Key|Refurbishment|769|4777|2/7/2025|10:26AM|Key returned
162|2/7/2025|8:46AM|M-A05-34|Key|Cleaning by OTS team|769|5658|2/9/2025|4:15pm|Key returned
163|2/9/2025|8:33AM|M-B07-47|2 Keys|PM Inspection|769|4257|2/9/2025|8:50AM|2 Keys returned
164|2/9/2025|11:15AM|M-B07-47|2 Keys|View|889|889|2/9/2025|12:09PM|2 Keys returned
165|2/9/2025|11:15AM|M-C15-22|Key|View|889|889|2/9/2025|12:09PM|Key returned
166|2/9/2025|11:15AM|M-C15-48|Key|View|889|889|2/9/2025|12:09PM|Key returned
167|2/9/2025|1:00PM|M-D20-28|3 Keys|Swapping cylinders of M-D20-28 & M-A04-25|6562|4777|2/9/2025|2:53PM|3 Keys returned
168|2/9/2025|2:53PM|M-B07-28|Key|Cleaning by OTS team|769|5658|2/12/2025|4:25PM|Key returned
169|2/10/2025|8:08AM|M-D20-28|Key|Refurbishment|769|5637|2/20/2025|7:52AM|Key returned
170|2/10/2025|8:31AM|M-A05-34|3 Keys|Inspection|769|4257|2/10/2025|11:27am|3 Keys returned
171|2/10/2025|1:00PM|M-B07-47|Key|Refurbishment|769|5637|2/10/2025|4:00PM|Key returned
172|2/10/2025|1:00PM|M-C11-13|Key|Refurbishment|769|5637|2/22/2025|8:14AM|Key returned
173|2/10/2025|3:15PM|M-D19-24|Key|View|769|769|2/10/2025|3:55PM|Key returned
174|2/13/2025|10:00AM|M-D20-35|3 Keys|Move in|6562|T-0057614|-|-|3 Keys issued
175|2/13/2025|1:50PM|M-C15-22|Key|View|769|769|2/13/2025|2:02PM|Key returned
176|2/15/2025|9:08AM|M-D19-24|Key|View|769|769|2/15/2025|9:35AM|Key returned
177|2/15/2025|11:00AM|M-B07-47|Key|Inspection|769|4257|2/15/2025|11:30AM|Key returned
178|2/16/2025|8:34AM|M-B07-47|Key|Cleaning by OTS team|769|1431|2/19/2025|2:10PM|Key returned
179|2/16/2025|1:31PM|M-B07-28|Key|View|769|769|2/16/2025|1:40PM|Key returned
180|2/17/2025|1:23PM|M-F28-44|Key|Refurbishment|769|5637|3/1/2025|2:00PM|Key Returned (3 keys for new cylinder received from 5637)
181|2/17/2025|3:43PM|M-D19-24|4 Keys|Refurbishment|769|4257|2/17/2025|5:28PM|4 Keys Returned
182|2/18/2025|1:32PM|M-B07-47|Key|View|769|769|2/18/2025|1:39PM|Key Returned
183|2/18/2025|3:00PM|M-A04-35|Key|View|889|889|2/18/2025|3:40pm|2 Keys returned
184|2/19/2025|8:14AM|M-F28-44|Key|View|889|889|2/19/2025|8:35AM|Key returned
185|2/19/2025|2:10PM|M-D19-24|Key|Cleaning by OTS team|6562|1431|2/21/2025|4:30PM|Key returned
186|2/19/2025|2:15PM|M-A05-34|3 Keys|View|889|889|2/19/2025|4:40pm|3 Keys returned
187|2/19/2025|2:15PM|M-D20-28|Key|View|889|889|2/19/2025|4:40pm|Key returned
188|2/20/2025|7:53AM|M-D20-28|Key|Refurbishment|769|6465|2/23/2025|1:30PM|Key returned
189|2/20/2025|1:41PM|M-C11-13|2 Keys|Inspection|769|4257|2/20/2025|4:30PM|2 Keys returned
190|2/22/2025|10:47AM|M-A04-35|Key|Cleaning by OTS team|769|1431|2/22/2025|12:30PM|Key returned
191|2/22/2025|1:13PM|M-C15-22|Key|View|769|769|2/22/2025|1:38PM|Key returned
192|2/23/2025|8:44AM|M-C11-13|Key|Cleaning by OTS team|769|5658|2/24/2025|11:00AM|Key returned
193|2/23/2025|11:57AM|M-C11-13|2 Keys|AC fixing by tenant|889|889|2/23/2025|12:57PM|2 Keys returned
194|2/24/2025|1:17PM|M-B07-47|Key|View|769|769|2/24/2025|1:24PM|Key returned
195|2/24/2025|1:30PM|M-A04-35|Key|Cleaning by OTS team|6562|5658|2/24/2025|2:09PM|Key returned
196|2/24/2025|2:08PM|M-C11-13|Key|Shifting items into flat|769|774|2/24/2025|2:30PM|Key returned
197|2/25/2025|9:44AM|M-A05-34|Key|Swapping cylinders of M-A05-34 & M-A04-35|769|5637|2/24/2025|10:09AM|Key returned
198|2/25/2025|9:44AM|M-A04-35|Key|Swapping cylinders of M-A05-34 & M-A04-35|769|5637|2/24/2025|10:09AM|Key returned
199|2/26/2025|7:40AM|M-D21-16|Key|Cleaning by OTS team|769|5658|2/26/2025|8:30AM|Key returned
200|2/26/2025|9:35AM|M-D20-28|Key|View|769|769|2/26/2025|9:46AM|Key returned
201|2/26/2025|10:30AM|M-D19-24|3 Keys|Move in|6562|T-0057567|-|-|3 Keys issued
202|2/26/2025|11:45PM|M-A04-35|2 Keys|Move in|6562|T-0057688|-|-|2 Keys issued
203|2/26/2025|12:00PM|M-C11-13|2 Keys|Move in|6562|T-0057638|-|-|2 Keys issued
204|-|-|M-B08-42|3 Keys|Move out|T-0027416|6562|2/27/2025|9:00AM|3 Keys returned
205|2/27/2025|3:30PM|M-B08-42|3 Keys|Move in|6562|T-0057787|-|-|3 Keys issued
206|-|-|M-C11-24|Key|Move out|T-0043276|6562|2/27/2025|10:00AM|Key returned
207|2/28/2025|10:08AM|M-D20-28|Key|Cleaning by OTS team|769|5658|2/28/2025|3:00PM|Key returned
208|3/2/2025|12:14PM|M-F28-44|Key|PM Inspection|769|5637|3/2/2025|1:00PM|Key returned
209|3/2/2025|1:16PM|M-F28-44|Key|Cleaning by OTS team|769|5658|3/5/2025|4:45PM|Key returned
210|3/3/2025|9:28AM|M-B07-28|Key|Inspection|769|5637|3/3/2025|9:42AM|Key returned
211|3/3/2025|9:50AM|M-C11-24|Key|Refurbishment|769|5637|3/9/2025|1:30PM|Key returned
212|3/3/2025|2:15PM|M-C15-48|Key|View|769|769|3/3/2025|2:25PM|Key returned
213|3/3/2025|2:15PM|M-B07-28|2 Keys|Move in|6562|T-0057805|-|-|2 Keys issued
214|-|-|M-E26-23|2 Keys|Move out|T-0027745|6562|3/3/2025|2:20PM|2 Keys returned
215|3/3/2025|3:00PM|M-E26-23|2 Keys|Move in|6562|T-0057854|-|-|2 Keys issued
216|3/4/2025|11:50AM|M-C11-24|Key|View|769|769|3/4/2025|12:00PM|Key returned
217|3/6/2025|2:25PM|M-C15-48|Key|PM Inspection|769|769|3/6/2025|2:49PM|Key returned
218|3/6/2025|2:25PM|M-D20-28|Key|PM Inspection|769|769|3/6/2025|2:49PM|Key returned
219|3/6/2025|2:25PM|M-F28-44|Key|PM Inspection|769|769|3/6/2025|2:49PM|Key returned
220|3/6/2025|2:25PM|M-C11-24|Key|PM Inspection|769|769|3/6/2025|2:49PM|Key returned
221|3/8/2025|9:27AM|M-F28-44|Key|View|769|769|3/8/2025|9:42AM|Key returned
222|3/9/2025|9:30AM|M-B07-28|Key|Refurbishment|6562|4777|3/9/2025|2:05PM|Key returned
223|3/9/2025|10:00AM|M-C15-22|Key|Refurbishment|769|5637|3/10/2025|1:31PM|Key returned
224|3/9/2025|10:00AM|M-C15-48|Key|Refurbishment|769|5637|3/10/2025|1:31PM|Key returned
225|3/10/2025|1:10PM|M-D20-28|Key|View|774|774|3/10/2025|1:25PM|Key returned
226|3/10/2025|2:00PM|M-C11-24|Key|Cleaning by OTS team|769|5658|3/18/2025|4:30PM|Key returned
227|3/12/2025|11:35AM|M-D20-28|Key|View|769|769|3/12/2025|11:50AM|Key returned
228|3/13/2025|10:00AM|M-B07-47|3 Keys|Move in|6562|T-0057817|-|-|3 Keys issued
229|3/13/2025|11:20AM|M-C11-24|2 Keys|PM Inspection|6562|6562|3/13/2025|11:52AM|2 Keys returned
230|3/13/2025|12:05PM|M-A01-36|Key|View|769|769|3/13/2025|12:25PM|Key returned
231|3/13/2025|12:05PM|M-C15-48|Key|View|769|769|3/13/2025|12:25PM|Key returned
232|3/15/2025|1:47AM|M-C11-47|Key|Opening main door refer to work order #2751491|395|395|3/15/2025|2:00AM|Key returned
233|3/15/2025|2:00PM|M-A05-34|Key|Refurbishment|769|5637|3/17/2025|11:45AM|Key returned
234|3/15/2025|2:00PM|M-F28-44|Key|Refurbishment|769|5637|3/17/2025|12:20PM|Key returned
235|3/15/2025|3:25PM|M-A01-36|Key|Cleaning by OTS team|769|5658|3/16/2025|8:45AM|Key returned
236|-|-|M-A01-36|3 Keys|Move out|T-0044334|6562|3/16/2025|1:15PM|3 Keys returned
237|3/17/2025|11:06AM|M-C11-24|Key|View|769|769|3/17/2025|11:35AM|Key returned
238|3/18/2025|8:19AM|M-A01-36|Key|Refurbishment|769|5637|3/29/2025|2:03PM|Key returned
239|3/18/2025|1:21PM|M-F28-44|Key|Refurbishment|769|5637|3/18/2025|2:00PM|Key returned
240|3/18/2025|2:00PM|M-F28-44|Key|Cleaning by OTS team|769|1431|3/18/2025|4:30PM|Key returned
241|3/19/2025|10:20AM|M-A05-34|Key|View|769|769|3/19/2025|10:32AM|Key returned
242|3/20/2025|1:00PM|M-D20-28|Key|View|769|769|3/20/2025|1:15PM|Key returned
243|3/22/2025|11:02AM|M-D20-28|Key|View|769|769|3/22/2025|11:30AM|Key returned
244|3/22/2025|1:27PM|M-A05-34|Key|View|769|769|3/22/2025|1:55PM|Key returned
245|3/23/2025|11:50AM|M-C11-24|Key|Refurbishment|769|4777|3/23/2025|12:36PM|Key returned
246|3/23/2025|11:50AM|M-C15-22|Key|Refurbishment|769|4777|3/23/2025|12:36PM|Key returned
247|3/23/2025|11:50AM|M-F28-44|Key|Refurbishment|769|4777|3/23/2025|12:36PM|Key returned
248|3/23/2025|11:50AM|M-A05-34|Key|Refurbishment|769|4777|3/23/2025|12:36PM|Key returned
249|3/23/2025|11:50AM|M-C15-48|Key|Refurbishment|769|4777|3/23/2025|12:36PM|Key returned
250|3/26/2025|1:11PM|M-A01-36|Key|View|769|769|3/26/2025|1:27PM|Key returned
251|3/26/2025|3:20PM|M-C11-24|Key|Cleaning by OTS team|769|5759|3/26/2025|4:25PM|Key returned
252|3/27/2025|9:30AM|M-C11-24|Key|Refurbishment|769|6465|3/27/2025|11:00AM|Key returned
253|-|-|M-B10-24|2 Keys|Move out|T-0035359|6562|3/27/2025|10:00AM|2 Keys returned
254|3/27/2025|2:00PM|M-C11-24|2 Keys|Move in|6562|T-0057950|-|-|2 Keys issued
255|3/27/2025|8:45PM|M-B9-42|Key|Opening main door refer to work order #2777059|807|807|3/27/2025|8:57PM|Key returned
256|-|-|M-A03-22|2 Keys|Move out|T-0035190|6562|3/29/2025|11:00AM|2 Keys returned
257|-|-|M-F31-38|2 Keys|Move out|T-0030202|6562|3/29/2025|11:30AM|2 Keys returned
258|3/29/2025|2:00PM|M-D20-28|Key|Move in|6562|T-0058044|-|-|Key issued
259|3/29/2025|2:00PM|M-F28-44|2 Keys|Move in|6562|T-0057826|-|-|2 Keys issued
260|3/29/2025|3:30PM|M-A05-34|2 Keys|Move in|6562|T-0057823|-|-|2 Keys issued
261|3/1/2024|12:00PM|M-A05-21|2 Keys|Move out|T-0053334|6562|4/7/2025|12:00PM|2 Keys returned
262|4/7/2025|2:45PM|M-C15-48|Key|FAFF PPM Inspection|769|787|4/7/2025|4:25PM|Key returned
263|4/7/2025|2:45PM|M-A01-36|Key|FAFF PPM Inspection|769|787|4/7/2025|4:25PM|Key returned
264|4/7/2025|2:45PM|M-A05-21|Key|FAFF PPM Inspection|769|787|4/7/2025|4:25PM|Key returned
265|4/7/2025|2:45PM|M-A03-22|Key|FAFF PPM Inspection|769|787|4/7/2025|4:25PM|Key returned
266|4/7/2025|2:45PM|M-F31-38|Key|FAFF PPM Inspection|769|787|4/7/2025|4:25PM|Key returned
267|4/8/2025|9:45AM|M-A03-22|Key|View|769|769|4/8/2025|9:53AM|Key returned
268|4/8/2025|1:35PM|M-C15-48|Key|View|769|769|4/8/2025|2:00PM|Key returned
269|4/9/2025|11:15AM|M-A05-21|Key|View|769|769|4/9/2025|11:36AM|Key returned
270|4/9/2025|11:15AM|M-F31-38|Key|View|769|769|4/9/2025|11:36AM|Key returned
271|4/10/2025|11:54AM|M-A03-22|Key|Refurbishment|769|5637|4/23/2025|12:40PM|Key returned
272|4/10/2025|9:00PM|M-C15-48|2 Keys|Shifting household items into flat|807|807|4/11/2025|2:35AM|2 Keys returned
273|4/12/2025|7:10AM|M-A05-21|Key|Refurbishment|769|5637|4/19/2025|6:20PM|2 Keys issued by shazad for new cylinder fixed
274|4/12/2025|12:00PM|M-C15-22|2 Keys|Move in|6562|T-0057889|-|-|2 Keys issued
275|4/13/2025|9:36AM|M-A01-36|Key|View|769|769|4/13/2025|10:06AM|Key returned
276|4/13/2025|9:36AM|M-A05-21|Key|View|769|769|4/13/2025|10:06AM|Key returned
277|4/13/2025|9:36AM|M-F31-38|Key|View|769|769|4/13/2025|10:06AM|Key returned
278|7/1/2010|12:00PM|M-A06-46|3 Keys|Move out|T-0027368|6562|4/13/2025|12:00PM|3 Keys returned
279|4/13/2025|2:15PM|M-A01-36|Key|View|769|769|4/13/2025|2:37PM|Key returned
280|4/13/2025|2:15PM|M-A05-21|Key|View|769|769|4/13/2025|2:37PM|Key returned
281|4/13/2025|2:15PM|M-F31-38|Key|View|769|769|4/13/2025|2:37PM|Key returned
282|4/13/2025|3:12PM|M-D17-24|Key|Request for access- Ref work order no. 2808740|769|769|4/13/2025|3:20PM|Key returned
283|4/14/2025|2:30PM|M-F31-38|Key|View|889|889|4/14/2025|2:45PM|Key returned
284|2/1/2010|12:00PM|M-B09-33|2 Keys|Move out|T-0027435|6562|4/15/2025|2:00PM|2 Keys returned
285|4/16/2025|11:27AM|M-A06-46|Key|Refurbishment|769|5637|5/3/2025|7:55AM|Key returned
286|4/16/2025|11:45AM|M-A01-36|Key|View|769|769|4/16/2025|12:10PM|Key returned
287|4/16/2025|3:12PM|M-F31-38|Key|Refurbishment|769|5637|4/23/2025|12:40PM|Key returned
288|4/17/2025|12:00PM|M-C15-48|2 Keys|Move in|6562|T-0057938|-|-|2 Keys issued
289|4/19/2025|6:20PM|M-A05-21|2 Keys|2 Keys issued by shazad - new cylinder fixed|5637|769|4/20/2025|6:20PM|2 Keys issued/ returned
290|4/20/2025|12:45PM|M-A06-46|Key|View|774|774|4/20/2025|12:54PM|Key returned
291|4/21/2025|3:54PM|M-C15-22|Key|Refurbishment|769|5637|4/21/2025|5:45PM|Key returned
292|4/22/2025|9:40AM|M-F31-38|Key|View|769|769|4/22/2025|10:11AM|Key returned
293|4/22/2025|10:40AM|M-A03-22|Key|View|769|769|4/22/2025|11:20AM|Key returned
294|4/22/2025|4:22PM|M-A03-27|Key|Request for access- Ref work order no. 2827010|889|889|4/22/2025|6:32PM|Key returned
295|4/23/2025|9:55AM|M-B09-33|Key|Cleaning by OTS team|769|5658|4/23/2025|4:30PM|Key returned
296|4/24/2025|8:16AM|M-A03-22|Key|Cleaning by OTS team|769|5658|4/25/2025|4:30PM|Key returned
297|4/25/2025|1:02PM|M-A01-36|Key|Cleaning by OTS team|769|5658|4/25/2025|4:30PM|Key returned
298|4/25/2025|1:02PM|M-C15-45|Key|Request for access- Ref work order no. 2831520|889|889|4/25/2025|1:20PM|Key returned
299|4/26/2025|8:27AM|M-F31-38|Key|Cleaning by OTS team|769|5658|4/30/2025|3:20PM|Key returned
300|4/27/2025|9:31AM|M-A01-36|Key|Inspection|769|4257|4/27/2025|10:45AM|Key returned
"""

    private const val CHUNK_3 = """
301|4/28/2025|8:37AM|M-A03-22|Key|Cleaning by OTS team|769|5658|4/28/2025|10:10AM|Key returned
302|4/28/2025|12:52PM|M-A05-21|Key|View|769|769|4/28/2025|1:07PM|Key returned
303|4/29/2025|2:00PM|M-A03-22|2 Keys|Move in|6562|T-0058334|-|-|2 Keys issued
304|4/29/2025|7:58AM|M-A03-22|Key|Refurbishment|769|6465|4/29/2025|3:05PM|Key returned
305|4/30/2025|7:34AM|M-A03-22|Key|Refurbishment|6562|6465|5/1/2025|3:07PM|Key returned
306|4/30/2025|8:35AM|M-A05-21|Key|Cleaning by OTS team|889|5658|5/2/2025|4:45PM|Key returned
307|4/30/2025|10:36AM|M-C15-45|Key|Request for access- Ref work order no. 2842538|769|769|4/30/2025|10:40AM|Key returned
308|4/30/2025|2:00PM|M-A01-36|2 Keys|Move in|6562|T-0058392|-|-|2 Keys issued
309|4/30/2025|2:15PM|M-F31-38|3 Keys|Swapping cylinders of M-F31-38 & M-A03-22|769|4777|4/30/2025|2:42PM|1 Key returned for F31-38; 3 Keys returned for A3-22 (F31-38 A3-22 Cylinder swap)
310|5/3/2025|9:29AM|M-A05-21|Key|Inspection|6562|4257|5/3/2025|10:17AM|Key returned
311|1/3/2022|-|M-C13-26|2 Keys|Move out|T-0043712|6562|5/3/2025|11:30AM|2 Keys returned
312|5/3/2025|10:49AM|M-A05-21|2 Keys|Refurbishment|769|5637|5/3/2025|11:30AM|2 Keys returned
313|5/4/2025|9:30AM|M-A05-21|Key|Refurbishment|6562|6485|5/4/2025|10:15AM|Key returned
314|5/4/2025|10:28AM|M-C13-26|3 Keys|Inspection|769|4777|5/4/2025|10:43AM|3 Keys returned
315|5/4/2025|2:31PM|M-A06-46|Key|Pre Inspection|769|4257|5/4/2025|3:40PM|Key returned
316|5/5/2025|7:35AM|M-A06-46|Key|Refurbishment|6562|6485|5/6/2025|8:20AM|Key returned
317|5/6/2025|1:01PM|M-C13-26|Key|View|769|769|5/6/2025|1:30PM|Key returned
318|5/7/2025|9:25AM|M-C13-26|Key|Split AC Inspection|769|6477|5/6/2025|10:45AM|Key returned
319|5/7/2025|12:00PM|M-A05-21|2 Keys|Move in|6562|T-0058496|-|-|2 Keys issued
320|5/7/2025|4:43PM|M-C13-26|Key|Refurbishment|769|6485|5/18/2025|10:12AM|Key returned
321|5/11/2025|1:10PM|M-A06-46|Key|Cleaning by OTS team|769|5658|5/14/2025|10:45AM|Key returned
322|5/15/2025|12:00PM|M-A06-46|3 Keys|Move in|6562|T-0058505|-|-|3 Keys issued
323|5/15/2025|2:00PM|M-B09-33|2 Keys|Move in|6562|T-0058493|-|-|2 Keys issued
324|5/17/2025|11:15AM|M-C13-26|2 Keys|View|774|774|5/17/2025|11:33AM|2 Keys returned
325|5/18/2025|1:15PM|M-C13-26|Key|Cleaning by OTS team|769|5658|5/22/2025|4:20PM|Key returned
326|5/18/2025|4:53PM|M-C13-26|2 Keys|Pre Inspection|769|4257|5/18/2025|5:30PM|2 Keys returned
327|5/20/2025|8:09AM|M-C13-26|Key|Refurbishment|769|6492|5/20/2025|4:00PM|Key returned
328|5/22/2025|8:58AM|M-C13-26|Key|Refurbishment|6562|6485|5/22/2025|9:52AM|Key returned
329|5/24/2025|7:20AM|M-C11-21|Key|Request for access- Ref work order no. 2899127|769|769|5/24/2025|7:25AM|Key returned
330|5/26/2025|2:55PM|M-F31-38|Key|Move in pre Inspection|6562|6599|5/26/2025|3:30PM|Key returned
331|5/28/2025|12:20AM|M-A06-47|Key|Request for access- Ref work order no. 2907902|395|395|5/28/2025|12:30AM|Key returned
332|5/28/2025|8:25PM|M-C15-48|Key|Request for access- Ref work order no. 2910318|395|395|5/28/2025|10:00PM|Key returned
333|5/28/2025|12:00PM|M-D20-12|2 Keys|Transfer|6562|T-0044636|-|-|2 Keys issued
334|5/29/2025|12:00PM|M-C13-26|2 Keys|Move in|6562|T-0058714|-|-|2 Keys issued
335|5/29/2025|2:00PM|M-F31-38|Key|Move in|6562|T-0058410|-|-|1 Key issued
336|2/1/2015|-|M-F31-13|3 Keys|Move out|T-0041137|6562|5/29/2025|2:00PM|3 Keys returned
337|5/31/2025|11:07AM|M-E23-37|Key|Split AC disconnection for gas work|769|769|5/31/2025|11:34AM|Key returned
338|6/2/2025|10:24AM|M-A04-26|Key|Refurbishment|6562|6485|6/4/2025|8:25AM|Key returned
339|-|-|M-A05-31|Key|Move out|T-0046945|6562|6/3/2025|12:00PM|1 Key returned
340|-|-|M-D19-11|Key|Move out|T-0028163|6562|6/3/2025|2:00PM|1 Key returned
341|6/10/2025|7:25AM|M-A04-26|Key|Refurbishment|6562|6485|6/19/2025|8:16AM|Key returned
342|-|-|M-E26-35|2 Keys|Move out|T-0027751|6562|6/11/2025|2:00PM|2 Keys returned
343|6/11/2025|2:37PM|M-A04-26|Key|View|769|769|6/11/2025|2:45PM|Key returned
344|-|-|M-C11-45|Key|Move out|T-0027488|6562|6/12/2025|12:00PM|Keys returned
345|-|-|M-A04-42|2 Keys|Move out|T-0027312|6562|6/12/2025|12:00PM|2 Keys returned
346|6/12/2025|12:14PM|M-A04-42|Key|Inspection|769|4257|6/12/2025|1:05PM|Key returned
347|6/16/2025|7:30AM|M-A05-31|Key|Refurbishment|6562|6485|6/28/2025|9:40AM|Key returned
348|6/18/2025|8:45AM|M-F31-13|Key|Refurbishment|6562|6485|6/28/2025|10:50AM|Key returned
349|6/18/2025|12:45PM|M-A04-26|Key|View|774|774|6/18/2025|1:05PM|Key returned
350|6/18/2025|1:36PM|M-C11-45|Key|View|769|769|6/18/2025|1:50PM|Key returned
351|6/18/2025|5:20PM|M-C12-21|Key|Request for access- Ref work order no. 2953155|769|769|6/18/2025|5:27PM|Key returned
352|6/19/2025|8:16AM|M-E26-35|Key|Refurbishment|769|6485|7/6/2025|10:18AM|Key returned
353|6/21/2025|8:45AM|M-A04-26|Key|Refurbishment|769|4777|6/21/2025|5:00PM|Key returned
354|6/21/2025|9:15AM|M-C11-45|Key|Refurbishment|6562|6485|7/6/2025|10:18AM|Key returned
355|6/22/2025|2:45PM|M-D19-11|Key|View|744|744|6/22/2025|3:07PM|Key returned
356|6/23/2025|8:00AM|M-A04-26|Key|Cleaning by OTS team|6562|5658|6/24/2025|4:08PM|Key returned
357|6/23/2025|10:57AM|M-E26-35|Key|View|769|769|6/23/2025|11:08AM|Key returned
358|6/23/2025|5:35PM|M-F31-13|Key|Move out Inspection|769|4257|6/23/2025|6:35PM|Key returned
359|6/24/2025|7:40AM|M-A04-42|Key|Refurbishment|6562|6485|7/15/2025|5:30PM|Key returned
360|6/24/2025|1:50PM|M-A04-42|Key|View|769|769|6/24/2025|2:00PM|Key returned
361|6/26/2025|8:19AM|M-A05-31|Key|Cleaning by OTS team|769|5658|6/27/2025|4:30PM|Key returned
362|6/26/2025|1:45PM|M-E26-35|Key|View|769|769|6/26/2025|2:00PM|Key returned
363|6/28/2025|8:00AM|M-A04-42|2 Keys|View|769|769|6/28/2025|8:30AM|2 Keys returned
364|6/28/2025|10:00AM|M-F31-13|Key|Refurbishment( installation of new water heater)|6562|4365|6/28/2025|3:40PM|Key returned
365|6/28/2025|10:40AM|M-F31-13|Key|Cleaning by OTS team|769|5658|6/30/2025|3:00PM|Key returned
366|6/28/2025|12:00PM|M-A05-31|Key|Move in|6562|T-0059107|-|-|1 Key issued
367|6/28/2025|1:00PM|M-E23-48|Key|Refurbishment ( Silicon work)|6562|6485|6/28/2025|1:40PM|Key returned
368|-|-|M-C11-14|2 Keys|Move out|T-0027469|6562|6/28/2025|2:00PM|2 Keys returned
369|7/1/2025|8:22AM|M-A03-21|Key|FAFF PPM Inspection|769|787|7/1/2025|9:30AM|Key returned
370|7/1/2025|8:22AM|M-A04-42|2 Keys|FAFF PPM Inspection|769|787|7/1/2025|9:30AM|2 Keys returned
371|7/1/2025|8:22AM|M-E26-35|2 Keys|FAFF PPM Inspection|769|787|7/1/2025|9:30AM|2 Keys returned
372|7/1/2025|8:22AM|M-C11-14|3 Keys|FAFF PPM Inspection|769|787|7/1/2025|9:30AM|3 Keys returned
373|7/1/2025|8:22AM|M-C11-45|2 Keys|FAFF PPM Inspection|769|787|7/1/2025|9:30AM|2 Keys returned
374|7/1/2025|8:22AM|M-D19-11|2 Keys|FAFF PPM Inspection|769|787|7/1/2025|9:30AM|2 Keys returned
375|7/1/2025|10:19AM|M-C11-14|Key|Refurbishment|769|5637|7/14/2025|7:22AM|Key returned
376|7/1/2025|10:19AM|M-D19-11|2 Keys|Refurbishment|769|5637|7/7/2025|2:30PM|2 Keys returned
377|7/1/2025|11:16AM|M-F31-13|Key|View|769|769|7/1/2025|11:42AM|Key returned
378|-|-|M-E23-48|3 Keys|Move out|T-0028045|6562|7/1/2025|12:00PM|3 Keys returned
379|7/1/2025|12:00PM|M-A04-26|2 Keys|Move in|6562|T-0059166|-|-|2 Keys issued
380|7/1/2025|12:35PM|M-E26-35|Key|View|769|769|7/1/2025|12:47PM|Key returned
381|-|-|M-C11-25|2 Keys|Move out|T-0042038|6562|7/1/2025|2:00PM|2 Keys returned
382|-|-|M-A03-21|1 Key|Move out|T-0027273|6562|7/1/2025|2:00PM|1 Key returned
383|7/2/2025|8:00AM|M-C11-14|Key|View|769|769|7/2/2025|8:15AM|Key returned
384|7/2/2025|10:25AM|M-D19-11|Key|View|769|769|7/2/2025|10:35AM|Key returned
385|7/3/2025|10:35AM|M-D19-11|Key|View|769|769|7/3/2025|10:50AM|Key returned
386|7/5/2025|12:30PM|M-D19-11|Key|View|769|769|7/5/2025|12:50PM|Key returned
387|7/6/2025|8:50AM|M-C11-45|Key|Cleaning by OTS team|769|5658|7/7/2025|4:40PM|Key returned
388|7/6/2025|11:23AM|M-C11-45|Key|View|769|769|7/6/2025|11:33AM|Key returned
389|7/6/2025|2:15PM|M-C11-25|Key|View|769|769|7/6/2025|2:25PM|Key returned
390|-|-|M-C14-41|2 Keys|Move out|T-0050369|6562|7/6/2025|2:31PM|2 Keys returned
391|7/6/2025|2:19PM|M-E23-48|Key|View|889|889|7/6/2025|2:43PM|Key returned
392|7/7/2025|7:34AM|M-E26-35|Key|Refurbishment by Ideal co.|889|4777|7/10/2025|8:00AM|Key returned
393|7/7/2025|2:04PM|M-C11-25|Key|View|889|889|7/7/2025|2:31PM|Key returned
394|-|-|M-F31-27|2 Keys|Move out|T-0027823|6562|7/7/2025|3:00PM|2 Keys returned
395|7/8/2025|7:45AM|M-A03-21|Key|Refurbishment|769|6485|7/21/2025|1:00PM|Key returned
396|7/8/2025|7:45AM|M-C11-25|Key|Refurbishment|769|6485|7/10/2025|5:50PM|Key returned
397|7/8/2025|7:45AM|M-E23-48|Key|Refurbishment|769|6485|7/14/2025|7:22AM|Key returned
398|7/8/2025|8:00AM|M-F31-45|2 Keys|Inspection ( Move out)|769|4695|7/8/2025|9:40AM|2 Keys returned
399|7/8/2025|10:35AM|M-C14-41|2 Keys|View|769|769|7/8/2025|11:00AM|2 Keys returned
400|7/8/2025|11:42AM|M-A04-42|Key|Cleaning by OTS team|769|5658|7/11/2025|11:00AM|Key returned
401|-|-|M-F31-45|Key|Move out|T-0041836|6562|7/8/2025|2:38PM|Key returned
402|-|-|M-F28-17|2 Keys|Move out|T-0027865|6562|7/8/2025|3:00PM|2 Keys returned
403|7/8/2025|2:30PM|M-C14-41|3 Keys|Inspection ( Move out)|6562|4695|7/8/2025|6:00PM|3 Keys returned
404|7/9/2025|8:30AM|M-C11-25|Key|Refurbishment ( AC Work)|769|6477|7/9/2025|8:41AM|Key returned
405|7/9/2025|9:35AM|M-F31-27|3 Keys|View|769|769|7/9/2025|9:50AM|3 Keys returned
406|7/9/2025|2:15PM|M-F31-45|2 Keys|View|769|769|7/9/2025|2:35PM|2 Keys returned
407|7/10/2025|7:39AM|M-F31-13|Key|Refurbishment|769|4777|7/10/2025|5:46PM|Key returned
408|7/10/2025|7:39AM|M-C11-45|Key|Refurbishment|769|4777|7/11/2025|4:00PM|Key returned
409|7/10/2025|7:42AM|M-C11-25|Key|Refurbishment|769|6465|7/10/2025|3:45PM|Key returned
410|7/11/2025|11:20AM|M-E26-35|Key|Cleaning by OTS team|769|5658|7/13/2025|4:45PM|Key returned
411|7/12/2025|2:45PM|M-E23-48|3 Keys|View|769|769|7/12/2025|3:00PM|3 Keys returned
412|7/12/2025|2:50PM|M-C11-45|Key|Refurbishment|6562|4777|7/12/2025|3:05PM|Key returned
413|7/13/2025|8:30AM|M-C11-25|Key|Inspection|6562|4695|7/13/2025|8:58AM|Key returned
414|7/13/2025|9:00AM|M-F28-17|Key|Refurbishment|6562|5637|7/29/2025|5:06PM|Key returned
415|7/13/2025|9:00AM|M-F31-45|Key|Refurbishment|6562|5637|7/29/2025|5:06PM|Key returned
416|7/13/2025|9:00AM|M-F31-27|Key|Refurbishment|6562|5637|7/28/2025|12:33PM|Key returned
417|7/14/2025|8:30AM|M-C11-25|Key|Cleaning by OTS team|769|5658|7/19/2025|4:30PM|Key returned
418|7/14/2025|9:35AM|M-C11-25|Key|View|769|769|7/14/2025|9:45AM|Key returned
419|7/14/2025|12:48PM|M-E26-35|Key|View|889|889|7/14/2025|1:27PM|Key returned
420|7/14/2025|1:25PM|M-A03-21|Key|View|769|769|7/14/2025|1:36PM|Key returned
421|7/14/2025|1:25PM|M-E23-48|Key|View|889|889|7/14/2025|2:01PM|Key returned
422|-|-|M-C11-31|Key|Move out|T-0041864|6562|7/14/2025|2:00PM|1 Key returned
423|7/14/2025|5:40PM|M-C11-31|2 Keys|Inspection ( Move out)|769|4695|7/14/2025|6:30PM|2 Keys returned
424|7/15/2025|4:52PM|M-A04-42|Key|Refurbishment ( Kitchen cabinet work)|769|4777|7/15/2025|5:30PM|Key returned
425|7/16/2025|7:21AM|M-F28-17|Key|Refurbishment ( Kitchen cabinet work)|769|4777|7/16/2025|12:00PM|Key returned
426|7/16/2025|10:30AM|M-C14-41|2 Keys|View|769|769|7/16/2025|11:05AM|2 Keys returned
427|7/16/2025|1:54PM|M-E23-48|Key|View|769|769|7/16/2025|2:05PM|Key returned
428|7/16/2025|2:24PM|M-F31-27|Key|View|744|744|7/16/2025|2:33PM|Key returned
429|7/16/2025|2:28PM|M-B09-32|Key|View|889|889|7/16/2025|2:50PM|Key returned
430|-|-|M-B09-32|Key|Move out|T-0035222|6562|7/16/2025|2:50PM|1 Key returned
431|7/17/2025|11:00AM|M-C11-14|3 Keys|Barwa inspection|769|6562|7/17/2025|12:06PM|3 Keys returned
432|7/17/2025|11:00AM|M-C11-45|3 Keys|Barwa inspection|769|6562|7/17/2025|12:06PM|3 Keys returned
433|7/17/2025|1:43PM|M-A04-42|2 Keys|View|769|769|7/17/2025|1:51PM|2 Keys returned
434|7/17/2025|2:54PM|M-C11-45|Key|Cleaning by OTS team|769|5658|7/17/2025|3:50PM|Key returned
435|7/19/2025|11:13AM|M-C11-14|3 Keys|View|769|769|7/19/2025|11:32AM|3 Keys returned
436|7/19/2025|2:00PM|M-E26-35|2 Keys|Move in|6562|T-0059211|-|-|2 Keys issued
437|7/20/2025|7:40AM|M-A04-42|Key|Refurbishment ( Kitchen cabinet work)|769|4777|7/21/2025|4:00PM|Key returned
438|7/20/2025|12:45PM|M-F28-17|2 Keys|View|769|769|7/20/2025|12:57PM|2 Keys returned
439|7/20/2025|1:25PM|M-C14-41|Key|Cleaning by OTS team|769|5658|7/20/2025|4:30PM|Key returned
440|7/20/2025|1:25PM|M-D19-11|Key|Cleaning by OTS team|769|5658|7/22/2025|4:05PM|Key returned
"""

    private const val CHUNK_TAIL = """
1|-|-|M-C15-21|Key|Move out|-|T-0035439 / Mr. Rodel Cabrales|10/31/2026|2:00PM|Unnumbered row after SI 1567 • 1 Key returned
1283|7/7/2026|9:05AM|M-B09-12|Key|Key duplication- Ref work order no. 3797715|Mr. Rodel Cabrales|Mr. Emmanuel Ssali|-|-|1 Key not yet returned
1634|11/26/2026|09:24AM|M-A04-16|1 Key|Refurbishment Cleaning|Mr. Israel Agbofa|Mr. Mansu Khan|-|-|1 Key not yet returned
1635|11/26/2026|11:28AM|Store Near Gate Exit|4 Keys|Opening|Mr. Charles|Mr. Israel Agbofa|11/26/2026|12:11PM|4 Keys returned
1636|11/26/2026|12:26PM|M-C13-16|2 Keys|Viewing|Mr. Charles|Mr. Israel Agbofa|11/26/2026|12:42PM|2 Keys returned
1637|11/26/2026|12:26PM|M-C15-21|1 Key|Viewing|Mr. Charles|Mr. Israel Agbofa|11/26/2026|12:42PM|1 Key returned
1638|11/26/2026|08:38AM|M-E25-34|2 Keys|Move in|Mr. Shajith|Pending T-code|-|-|Move-In Keys Given to Client • Pending T-code from Mr. Shajith
1639|11/26/2026|10:08AM|M-C13-47|3 Keys|Move in|Mr. Shajith|Pending T-code|-|-|Move-In Keys Given to Client • Pending T-code from Mr. Shajith
1640|11/26/2026|12:40PM|M-A04-16|1 Key|PM Inspection|Mr. Charles|PM Shajith|11/26/2026|13:05PM|1 Key returned
1641|11/26/2026|12:40PM|M-D18-41|1 Key|PM Inspection|Mr. Charles|PM Shajith|11/26/2026|13:05PM|1 Key returned
1642|11/26/2026|12:46PM|M-F30-18|1 Key|PM Inspection|Mr. Charles|PM Shajith|11/26/2026|13:05PM|1 Key returned
1643|11/26/2026|13:36PM|M-F30-18|1 Key|Refurbishment|Mr. Aqib Rehman|Mr. Noor Alam|-|-|1 Key not yet returned
1644|11/26/2026|14:34PM|M-D20-13|1 Key|Move in|Mr. Shajith|Pending T-code (Mr. Shajith)|-|-|Move-In Keys Given to Client • Pending T-code from Mr. Shajith
1645|11/26/2026|14:35PM|M-A01-35|2 Keys|Move in|Mr. Shajith|Pending T-code (Mr. Shajith)|-|-|Move-In Keys Given to Client • Pending T-code from Mr. Shajith
"""
}
