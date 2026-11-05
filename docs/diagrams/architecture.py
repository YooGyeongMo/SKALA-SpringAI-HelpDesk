"""HelpDesk 아키텍처 그림 생성 스크립트.

실행: pip install diagrams (graphviz 필요) 후
    python docs/diagrams/architecture.py
결과: docs/images/architecture.png
"""
import os

from diagrams import Cluster, Diagram, Edge
from diagrams.onprem.ci import GithubActions
from diagrams.onprem.client import User, Users
from diagrams.onprem.container import Docker
from diagrams.onprem.database import Postgresql
from diagrams.onprem.monitoring import Prometheus
from diagrams.onprem.network import Internet
from diagrams.programming.flowchart import Action, Decision, Document, StoredData
from diagrams.programming.framework import Spring
from diagrams.programming.language import Java

FONT = "Apple SD Gothic Neo"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "images", "architecture")

graph_attr = {"fontname": FONT, "fontsize": "15", "pad": "0.4", "nodesep": "0.55", "ranksep": "0.9", "splines": "spline"}
node_attr = {"fontname": FONT, "fontsize": "12"}
edge_attr = {"fontname": FONT, "fontsize": "11"}

with Diagram("AI 상담 및 업무 처리 서비스 (Spring AI HelpDesk)", filename=OUT, show=False, direction="LR",
             outformat="png", graph_attr=graph_attr, node_attr=node_attr, edge_attr=edge_attr):
    customer = User("고객\n상담 화면 index.html")
    operator = Users("운영자\n운영 콘솔 admin.html")

    with Cluster("Spring Boot 3.5 + Spring AI 1.1.8 (Java 21)"):
        chat_api = Spring("ChatController\n/api/chat, /stream")
        admin_api = Spring("AdminController\n/api/admin/*")
        service = Java("HelpDeskService\n@Retryable / @Recover")
        cache = StoredData("SemanticCache\n(SimpleVectorStore,\n첫 턴만)")

        with Cluster("ChatClient Advisor 체인 (order 낮을수록 바깥)"):
            audit = Action("Audit 0")
            meter = Action("TokenMeter 10")
            guard = Decision("SafeGuard 100")
            memory = StoredData("Memory 200\n(윈도우 20)")
            qa = Document("QuestionAnswer 300\n(topK 5, 0.28)")
            audit >> meter >> guard >> memory >> qa

        with Cluster("@Tool (ToolContext userId)"):
            order_tool = Action("OrderTools\norderStatus")
            ticket_tool = Action("TicketTools\ncreateTicket / myTickets")

        tickets = StoredData("TicketRepository\nPENDING / APPROVED")
        ingest = Document("IngestService\nTika, 800토큰 청크")
        metrics = Prometheus("/actuator/prometheus\nMicrometer")

    with Cluster("Docker"):
        pg = Postgresql("pgvector (pg17)\nHNSW, cosine, 1536d")

    openai = Internet("OpenAI API\ngpt-4o-mini\ntext-embedding-3-small")
    ci = GithubActions("CI\n비밀 스캔, build, test")

    customer >> Edge(label="REST / SSE\nX-User-Id") >> chat_api >> service
    service >> Edge(label="첫 턴 조회") >> cache
    service >> Edge(label="prompt()") >> audit
    qa >> Edge(label="유사도 검색") >> pg
    qa >> Edge(label="chat + tool call") >> openai
    openai >> Edge(label="tool 호출 요청", style="dashed", constraint="false") >> order_tool
    openai >> Edge(style="dashed", constraint="false") >> ticket_tool
    service >> Edge(style="invis") >> order_tool
    ticket_tool >> Edge(label="접수만 (PENDING)") >> tickets
    operator >> Edge(label="3초 폴링 / 승인") >> admin_api
    admin_api >> Edge(label="approve (사람만)") >> tickets
    admin_api >> Edge(label="/ingest") >> ingest >> Edge(label="임베딩 저장") >> pg
    meter >> Edge(label="ai.tokens, ai.latency", style="dotted") >> metrics
    ci >> Edge(style="dotted", label="push / PR", constraint="false") >> chat_api
