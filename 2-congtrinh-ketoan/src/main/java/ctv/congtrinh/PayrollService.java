package ctv.congtrinh;

import ctv.congtrinh.PayrollDAO;
import ctv.congtrinh.PayrollDAO.*;
import ctv.util.Exporter;
import ctv.util.Session;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Nghiệp vụ 2.7–2.9: tổng hợp, chốt/mở khóa và thanh toán lương. */
public class PayrollService {
    private final PayrollDAO dao=new PayrollDAO();
    /** Quyền kiểm ở tầng nghiệp vụ (không chỉ ẩn nút ở giao diện). */
    private static void need(String code,String action){
        if(!Session.can(code))throw new ctv.util.BusinessException("Bạn không có quyền "+action+" (chức năng "+code+").");
    }
    public List<Period> periods(){return dao.periods();}
    public int createPeriod(LocalDate from){need("2.7","tạo kỳ lương");return dao.createPeriod(from,Session.userId());}
    public List<PayrollRow> preview(int period){need("2.7","xem bảng lương dự kiến");return dao.buildPreview(period);}
    public void calculate(int period){need("2.7","tính bảng lương");dao.saveDraft(period);}
    public List<PayrollRow> rows(int period){return dao.payrollRows(period);}
    public void close(int period){need("2.8","chốt bảng lương");dao.closePeriod(period,Session.userId());}
    public int requestUnlock(int period,String reason){need("2.8","yêu cầu mở khóa bảng lương");return dao.requestUnlock(period,Session.userId(),reason);}
    public List<Unlock> unlocks(){return dao.unlockRequests();}
    public void decide(int request,boolean approve){
        if(!Session.can("2.10"))throw new ctv.util.BusinessException("Bạn không có quyền duyệt yêu cầu mở khóa bảng lương (chức năng 2.10).");
        dao.decideUnlock(request,Session.userId(),approve);
    }
    public void pay(int detailId,String method,BigDecimal amount,LocalDate day,String ref,String account){need("2.9","ghi nhận thanh toán lương");dao.addPayment(detailId,method,amount,day,ref,account,Session.userId());}
    public List<DailyRow> dailyRows(LocalDate day){return dao.dailyRows(day);}
    public void payDaily(int worker,LocalDate workDate,String method,BigDecimal amount,LocalDate payDate,String ref,String account){need("2.9","ghi nhận thanh toán lương hằng ngày");dao.addDailyPayment(worker,workDate,method,amount,payDate,ref,account,Session.userId());}
    public Receiver receiver(int worker){return dao.receiver(worker);}
    public List<Payment> payments(Integer period,Integer worker,LocalDate from,LocalDate to,String status){return dao.payments(period,worker,from,to,status);}
    public Result paymentSummary(Integer period){return dao.paymentSummary(period);}
    public Result laborCost(int period,Integer site){return dao.laborCost(period,site);}
    public Result top(int period,int count,boolean highest){return dao.topIncome(period,count,highest);}
    public Result laborCostMonth(java.time.YearMonth month,Integer site){return dao.laborCostMonth(month,site);}
    public Result topMonth(java.time.YearMonth month,int count,boolean highest){return dao.topIncomeMonth(month,count,highest);}
    public Result detail(int period,int worker){return dao.detailByWorker(period,worker);}
    public void export(Result r,File f,boolean pdf){if(pdf)Exporter.toPdf(f,r.title(),r.headers(),r.rows());else Exporter.toExcel(f,r.title(),r.headers(),r.rows());}
}
