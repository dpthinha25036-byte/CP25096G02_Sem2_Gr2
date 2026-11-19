package ctv.congtrinh;

import ctv.util.BusinessException;
import ctv.util.DBConnection;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 2.7–2.9: kỳ công, chốt bảng lương, mở khóa có phê duyệt và thanh toán. */
public class PayrollDAO {
    public record Period(int id, String code, LocalDate from, LocalDate to, String status, String closedBy, LocalDateTime closedAt) {
        @Override public String toString(){return code+" | "+from+" – "+to+" ("+statusLabel(status)+")";}
    }
    public record PayrollRow(int detailId,int workerId,String code,String name,BigDecimal regular,BigDecimal overtime,BigDecimal total,String paymentStatus,BigDecimal paid,BigDecimal remaining){}
    public record Unlock(int id,int periodId,String periodLabel,String requester,String reason,LocalDateTime requestedAt,String status,String approver){}
    public record Payment(int id,String workerCode,String workerName,String periodLabel,String method,BigDecimal amount,LocalDate date,String reference,String account){}
    /** Thông tin nhận lương của công nhân: có số tài khoản = chuyển khoản, không có = lãnh lương tay. */
    public record Receiver(String name,String bank,String accountNo,String holder){
        public boolean hasAccount(){return accountNo!=null&&!accountNo.isBlank();}
        /** Chuỗi ngắn dùng điền sẵn vào ô "Tài khoản nhận" (lưu kèm mã tham chiếu, tối đa 50 ký tự). */
        public String accountText(){return !hasAccount()?"":(bank==null||bank.isBlank()?"":bank.trim()+" ")+accountNo;}
        private static String esc(String t){return t==null?"":t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
        public String html(){
            if(!hasAccount())return "<html><b>Lãnh lương tay</b><br>Công nhân "+esc(name)+" chưa đăng ký tài khoản ngân hàng.</html>";
            String h=holder==null||holder.isBlank()?name:holder;
            return "<html><b>Chuyển khoản</b>"+(bank==null||bank.isBlank()?"":"<br>Ngân hàng: "+esc(bank))+"<br>Số tài khoản: "+esc(accountNo)+"<br>Chủ tài khoản: "+esc(h)+"</html>";
        }
    }
    /** Một dòng trả lương hằng ngày: công nhân thời vụ có chấm công trong ngày, tiền công ngày, đã trả và còn lại. */
    public record DailyRow(int workerId,String code,String name,BigDecimal regular,BigDecimal overtime,BigDecimal wage,BigDecimal paid,BigDecimal remaining,boolean valid,boolean attended){}
    public static final String SEASONAL_TYPE="Thời vụ";
    /** Nhận diện loại công nhân thời vụ theo tên loại (không phân biệt hoa/thường, bỏ khoảng trắng thừa). */
    public static boolean isSeasonal(String typeName){return typeName!=null&&typeName.trim().toLowerCase().contains(SEASONAL_TYPE.toLowerCase());}
    public record Result(String title,List<String> headers,List<List<Object>> rows){}
    private static LocalDate d(ResultSet r,int i)throws SQLException{return r.getObject(i,LocalDate.class);}
    private static LocalDateTime dt(ResultSet r,int i)throws SQLException{Timestamp t=r.getTimestamp(i);return t==null?null:t.toLocalDateTime();}
    public static String statusLabel(String s){return switch(s){case "DRAFT"->"Nháp";case "CLOSED"->"Đã chốt / khóa";case "REOPENED"->"Đã mở khóa";default->s;};}

    public List<Period> periods(){return query("SELECT p.period_id,CONCAT('KL-',DATE_FORMAT(p.start_date,'%Y%m%d')),p.start_date,p.end_date,p.status,u.full_name,p.closed_at FROM payroll_periods p LEFT JOIN users u ON u.user_id=p.closed_by ORDER BY p.start_date DESC",
            r->new Period(r.getInt(1),r.getString(2),d(r,3),d(r,4),r.getString(5),r.getString(6),dt(r,7)));}
    public Period period(int id){List<Period> p=query("SELECT p.period_id,CONCAT('KL-',DATE_FORMAT(p.start_date,'%Y%m%d')),p.start_date,p.end_date,p.status,u.full_name,p.closed_at FROM payroll_periods p LEFT JOIN users u ON u.user_id=p.closed_by WHERE p.period_id=?",r->new Period(r.getInt(1),r.getString(2),d(r,3),d(r,4),r.getString(5),r.getString(6),dt(r,7)),id);if(p.isEmpty())throw new BusinessException("Không tìm thấy kỳ lương.");return p.get(0);}

    public int createPeriod(LocalDate from,int userId){
        if(from==null)throw new BusinessException("Chưa chọn ngày bắt đầu kỳ lương.");
        if(from.getDayOfWeek()!=DayOfWeek.THURSDAY)throw new BusinessException("Ngày bắt đầu kỳ lương phải là Thứ Năm.");
        LocalDate to=from.plusDays(6);
        try(Connection c=DBConnection.get();PreparedStatement ps=c.prepareStatement("INSERT INTO payroll_periods(start_date,end_date,status) VALUES(?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS)){
            try(PreparedStatement ck=c.prepareStatement("SELECT COUNT(*) FROM payroll_periods WHERE start_date=? OR (start_date<=? AND end_date>=?)")){ck.setObject(1,from);ck.setObject(2,to);ck.setObject(3,from);try(ResultSet rs=ck.executeQuery()){rs.next();if(rs.getInt(1)>0)throw new BusinessException("Kỳ lương bị trùng với kỳ đã tồn tại.");}}
            ps.setObject(1,from);ps.setObject(2,to);ps.executeUpdate();try(ResultSet k=ps.getGeneratedKeys()){k.next();return k.getInt(1);}
        }catch(SQLException e){throw new BusinessException("Không tạo được kỳ lương: "+e.getMessage(),e);}
    }

    public List<PayrollRow> buildPreview(int periodId){
        Period p=period(periodId);
        int invalid=scalar("SELECT COUNT(*) FROM attendances WHERE work_date BETWEEN ? AND ? AND validation_status<>'VALID'",p.from(),p.to());
        if(invalid>0)throw new BusinessException("Còn "+invalid+" bản ghi chấm công chưa hợp lệ/cần cảnh báo. Hãy xử lý trước khi tính lương.");
        return previewRows(p);
    }
    private List<PayrollRow> previewRows(Period p){
        return query("""
            SELECT w.worker_id,w.worker_code,w.full_name,COALESCE(SUM(a.regular_shifts),0),COALESCE(SUM(a.overtime_shifts),0),
                   COALESCE(SUM(a.regular_shifts*a.regular_rate+a.overtime_shifts*a.overtime_rate),0)
            FROM attendances a JOIN workers w ON w.worker_id=a.worker_id
            WHERE a.work_date BETWEEN ? AND ? AND a.validation_status='VALID'
            GROUP BY w.worker_id,w.worker_code,w.full_name ORDER BY w.worker_code""",
            r->new PayrollRow(0,r.getInt(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5),r.getBigDecimal(6),"UNPAID",BigDecimal.ZERO,r.getBigDecimal(6)),p.from(),p.to());
    }
    public void saveDraft(int periodId){
        Period p=period(periodId);
        if("CLOSED".equals(p.status()))throw new BusinessException("Kỳ đã chốt, cần yêu cầu mở khóa trước khi tính lại.");
        List<PayrollRow> rows=buildPreview(periodId);
        Set<Integer> keep=new HashSet<>();
        try(Connection c=DBConnection.get()){
            c.setAutoCommit(false);
            try {
                for(PayrollRow r:rows){
                    keep.add(r.workerId());
                    int detailId=0;BigDecimal paid=BigDecimal.ZERO;
                    try(PreparedStatement ps=c.prepareStatement("SELECT d.detail_id,COALESCE((SELECT SUM(p.amount) FROM wage_payments p WHERE p.detail_id=d.detail_id),0) FROM payroll_details d WHERE d.period_id=? AND d.worker_id=? FOR UPDATE")){
                        ps.setInt(1,periodId);ps.setInt(2,r.workerId());try(ResultSet rs=ps.executeQuery()){if(rs.next()){detailId=rs.getInt(1);paid=rs.getBigDecimal(2);}}
                    }
                    if(paid.compareTo(r.total())>0)throw new BusinessException("Không thể tính lại: số đã thanh toán của "+r.code()+" lớn hơn tiền công mới.");
                    String paymentStatus=paid.signum()==0?"UNPAID":paid.compareTo(r.total())>=0?"PAID":"PARTIAL";
                    if(detailId==0){
                        try(PreparedStatement ps=c.prepareStatement("INSERT INTO payroll_details(period_id,worker_id,regular_shifts,overtime_shifts,total_amount,payment_status) VALUES(?,?,?,?,?,?)")){ps.setInt(1,periodId);ps.setInt(2,r.workerId());ps.setBigDecimal(3,r.regular());ps.setBigDecimal(4,r.overtime());ps.setBigDecimal(5,r.total());ps.setString(6,paymentStatus);ps.executeUpdate();}
                    }else{
                        try(PreparedStatement ps=c.prepareStatement("UPDATE payroll_details SET regular_shifts=?,overtime_shifts=?,total_amount=?,payment_status=? WHERE detail_id=?")){ps.setBigDecimal(1,r.regular());ps.setBigDecimal(2,r.overtime());ps.setBigDecimal(3,r.total());ps.setString(4,paymentStatus);ps.setInt(5,detailId);ps.executeUpdate();}
                    }
                }
                List<int[]> stale=new ArrayList<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT detail_id,worker_id FROM payroll_details WHERE period_id=?")){ps.setInt(1,periodId);try(ResultSet rs=ps.executeQuery()){while(rs.next())if(!keep.contains(rs.getInt(2)))stale.add(new int[]{rs.getInt(1),rs.getInt(2)});}}
                for(int[] item:stale){
                    try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM wage_payments WHERE detail_id=?")){ps.setInt(1,item[0]);try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getBigDecimal(1).signum()>0)throw new BusinessException("Không thể xóa dòng lương đã phát sinh thanh toán (mã công nhân ID "+item[1]+").");}}
                    try(PreparedStatement ps=c.prepareStatement("DELETE FROM payroll_details WHERE detail_id=?")){ps.setInt(1,item[0]);ps.executeUpdate();}
                }
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof BusinessException b)throw b;throw e;}
        }catch(SQLException e){throw new BusinessException("Lỗi tạo bảng lương dự kiến: "+e.getMessage(),e);}
    }
    public List<PayrollRow> payrollRows(int periodId){
        return query("""
            SELECT d.detail_id,w.worker_id,w.worker_code,w.full_name,d.regular_shifts,d.overtime_shifts,d.total_amount,d.payment_status,
             COALESCE((SELECT SUM(p.amount) FROM wage_payments p WHERE p.detail_id=d.detail_id),0) paid
            FROM payroll_details d JOIN workers w ON w.worker_id=d.worker_id WHERE d.period_id=? ORDER BY w.worker_code""",
            r->{BigDecimal total=r.getBigDecimal(7),paid=r.getBigDecimal(9);return new PayrollRow(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6),total,r.getString(8),paid,total.subtract(paid));},periodId);
    }
    public void closePeriod(int periodId,int userId){
        Period p=period(periodId);
        if("CLOSED".equals(p.status()))throw new BusinessException("Kỳ lương đã được chốt.");
        // Kiểm tra lại từ dữ liệu hiện tại (không tin trạng thái đã lưu): công nhân nghỉ việc, phân công bị hủy... có thể đã thay đổi.
        new AttendanceDAO().validateRange(p.from(),p.to());
        int invalid=scalar("SELECT COUNT(*) FROM attendances WHERE work_date BETWEEN ? AND ? AND validation_status<>'VALID'",p.from(),p.to());
        if(invalid>0)throw new BusinessException("Không thể chốt: còn "+invalid+" bản ghi chấm công chưa hợp lệ.");
        if(scalar("SELECT COUNT(*) FROM payroll_details WHERE period_id=?",periodId)==0)throw new BusinessException("Chưa tạo bảng lương dự kiến.");
        // Bảng lương đã lưu phải còn khớp với chấm công hiện tại.
        Map<Integer,PayrollRow> saved=new HashMap<>();
        for(PayrollRow r:payrollRows(periodId))saved.put(r.workerId(),r);
        List<PayrollRow> now=previewRows(p);
        boolean match=now.size()==saved.size();
        for(PayrollRow r:now){
            PayrollRow o=saved.get(r.workerId());
            if(o==null||o.total().compareTo(r.total())!=0||o.regular().compareTo(r.regular())!=0||o.overtime().compareTo(r.overtime())!=0){match=false;break;}
        }
        if(!match)throw new BusinessException("Bảng lương đã lưu không còn khớp với chấm công hiện tại. Hãy tính lại bảng lương trước khi chốt.");
        exec("UPDATE payroll_periods SET status='CLOSED',closed_by=?,closed_at=NOW() WHERE period_id=?",userId,periodId);
    }
    public int requestUnlock(int periodId,int userId,String reason){
        if(!"CLOSED".equals(period(periodId).status()))throw new BusinessException("Chỉ yêu cầu mở khóa kỳ đã chốt.");
        if(reason==null||reason.isBlank())throw new BusinessException("Phải nhập lý do mở khóa.");
        try(Connection c=DBConnection.get();PreparedStatement ps=c.prepareStatement("INSERT INTO unlock_requests(period_id,requested_by,reason) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            ps.setInt(1,periodId);ps.setInt(2,userId);ps.setString(3,reason.trim());ps.executeUpdate();try(ResultSet k=ps.getGeneratedKeys()){k.next();return k.getInt(1);}
        }catch(SQLException e){throw new BusinessException("Không tạo được yêu cầu mở khóa: "+e.getMessage(),e);}
    }
    public List<Unlock> unlockRequests(){
        return query("""
            SELECT r.request_id,r.period_id,CONCAT('KL-',DATE_FORMAT(p.start_date,'%Y%m%d'),' (',p.start_date,' – ',p.end_date,')'),u.full_name,r.reason,r.requested_at,r.status,COALESCE(a.full_name,'')
            FROM unlock_requests r JOIN payroll_periods p ON p.period_id=r.period_id JOIN users u ON u.user_id=r.requested_by
            LEFT JOIN users a ON a.user_id=r.approved_by ORDER BY r.requested_at DESC""",
            r->new Unlock(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),dt(r,6),r.getString(7),r.getString(8)));
    }
    public void decideUnlock(int requestId,int approver,boolean approve){
        try(Connection c=DBConnection.get()){
            c.setAutoCommit(false);
            try{
                int periodId,requester;String status;
                try(PreparedStatement ps=c.prepareStatement("SELECT period_id,status,requested_by FROM unlock_requests WHERE request_id=? FOR UPDATE")){ps.setInt(1,requestId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new BusinessException("Không tìm thấy yêu cầu mở khóa.");periodId=rs.getInt(1);status=rs.getString(2);requester=rs.getInt(3);}}
                if(requester==approver)throw new BusinessException("Người duyệt phải khác người yêu cầu mở khóa. Hãy nhờ người có quyền duyệt khác xử lý.");
                if(!"PENDING".equals(status))throw new BusinessException("Yêu cầu này đã được xử lý.");
                try(PreparedStatement ps=c.prepareStatement("UPDATE unlock_requests SET status=?,approved_by=?,approved_at=NOW() WHERE request_id=?")){ps.setString(1,approve?"APPROVED":"REJECTED");ps.setInt(2,approver);ps.setInt(3,requestId);ps.executeUpdate();}
                if(approve)try(PreparedStatement ps=c.prepareStatement("UPDATE payroll_periods SET status='REOPENED' WHERE period_id=? AND status='CLOSED'")){ps.setInt(1,periodId);if(ps.executeUpdate()==0)throw new BusinessException("Kỳ lương không còn ở trạng thái đã chốt.");}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof BusinessException b)throw b;throw e;}
        }catch(SQLException e){throw new BusinessException("Lỗi phê duyệt mở khóa: "+e.getMessage(),e);}
    }
    /** Thông tin nhận lương (ngân hàng / lãnh tay) của một công nhân. */
    public Receiver receiver(int workerId){
        List<Receiver> l=query("SELECT full_name,bank_name,bank_account_no,bank_account_holder FROM workers WHERE worker_id=?",
            r->new Receiver(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),workerId);
        if(l.isEmpty())throw new BusinessException("Không tìm thấy công nhân.");
        return l.get(0);
    }
    private static final String ACC_SEP=" | TK ";
    private static String packReference(String ref,String account){
        String r=ref==null||ref.isBlank()?"":ref.trim(),a=account==null||account.isBlank()?"":account.trim();
        if(a.isEmpty())return r.isEmpty()?null:r;
        return r+ACC_SEP+a;
    }
    private static String refOf(String stored){if(stored==null)return null;int i=stored.indexOf(ACC_SEP);return i<0?stored:stored.substring(0,i);}
    private static String accountOf(String stored){if(stored==null)return null;int i=stored.indexOf(ACC_SEP);return i<0?null:stored.substring(i+ACC_SEP.length());}
    /** Kiểm tra dữ liệu thanh toán dùng chung; trả về chuỗi lưu vào reference_code. */
    private static String checkPayment(String method,BigDecimal amount,LocalDate day,String reference,String account){
        if(!List.of("CASH","TRANSFER").contains(method))throw new BusinessException("Phương thức thanh toán không hợp lệ.");
        if(amount==null||amount.signum()<=0)throw new BusinessException("Số tiền thanh toán phải lớn hơn 0.");
        if(day==null)throw new BusinessException("Chưa chọn ngày thanh toán.");
        if("TRANSFER".equals(method)&&(account==null||account.isBlank()))throw new BusinessException("Thanh toán chuyển khoản cần tài khoản nhận.");
        if("TRANSFER".equals(method)&&(reference==null||reference.isBlank()))throw new BusinessException("Thanh toán chuyển khoản cần mã tham chiếu giao dịch.");
        // Form 06 (wage_payments) không có cột tài khoản nhận: lưu kèm trong reference_code dạng "mã | TK số_tài_khoản".
        final String stored=packReference(reference,account);
        if(stored!=null&&stored.length()>50)throw new BusinessException("Mã tham chiếu kèm tài khoản nhận tối đa 50 ký tự (hiện "+stored.length()+").");
        return stored;
    }
    /** Danh sách công nhân thời vụ có chấm công trong ngày kèm tiền công ngày, số đã trả và còn lại. */
    public List<DailyRow> dailyRows(LocalDate day){
        if(day==null)throw new BusinessException("Chưa chọn ngày công.");
        // Liệt kê TẤT CẢ công nhân thời vụ (chưa nghỉ việc, hoặc đã có chấm công ngày đó), kèm trạng thái chấm công của ngày.
        return query("""
            SELECT w.worker_id,w.worker_code,w.full_name,COALESCE(a.regular_shifts,0),COALESCE(a.overtime_shifts,0),
                   COALESCE(a.regular_shifts*COALESCE(a.regular_rate,0)+a.overtime_shifts*COALESCE(a.overtime_rate,0),0),
                   COALESCE((SELECT SUM(p.amount) FROM wage_payments p JOIN payroll_details d ON d.detail_id=p.detail_id WHERE d.worker_id=w.worker_id AND p.work_date=?),0),
                   a.validation_status
            FROM workers w JOIN worker_types t ON t.worker_type_id=w.worker_type_id
            LEFT JOIN attendances a ON a.worker_id=w.worker_id AND a.work_date=?
            WHERE LOWER(TRIM(t.type_name)) LIKE ? AND (w.work_status<>'RESIGNED' OR a.attendance_id IS NOT NULL)
            ORDER BY (a.attendance_id IS NULL),w.worker_code""",
            r->{BigDecimal wage=r.getBigDecimal(6),paid=r.getBigDecimal(7);String vs=r.getString(8);return new DailyRow(r.getInt(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5),wage,paid,wage.subtract(paid),"VALID".equals(vs),vs!=null);},
            day,day,"%"+SEASONAL_TYPE.toLowerCase()+"%");
    }
    /**
     * Trả lương hằng ngày cho công nhân THỜI VỤ, không cần chờ chốt kỳ tuần. Khoản trả gắn vào dòng lương của kỳ chứa ngày công
     * (tự tạo/cập nhật dòng lương cho công nhân này nếu kỳ chưa chốt); cuối kỳ phần "còn lại" chỉ là số chưa trả.
     */
    public void addDailyPayment(int workerId,LocalDate workDate,String method,BigDecimal amount,LocalDate payDate,String reference,String account,int userId){
        if(workDate==null)throw new BusinessException("Chưa chọn ngày công.");
        final String stored=checkPayment(method,amount,payDate,reference,account);
        try(Connection c=DBConnection.get()){
            c.setAutoCommit(false);
            try{
                String type;
                try(PreparedStatement ps=c.prepareStatement("SELECT t.type_name FROM workers w JOIN worker_types t ON t.worker_type_id=w.worker_type_id WHERE w.worker_id=? FOR UPDATE")){ps.setInt(1,workerId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new BusinessException("Không tìm thấy công nhân.");type=rs.getString(1);}}
                if(!isSeasonal(type))throw new BusinessException("Chỉ công nhân thời vụ mới được thanh toán lương hằng ngày. Công nhân chính thức nhận lương theo kỳ.");
                BigDecimal dayWage;
                try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*),COALESCE(SUM(validation_status<>'VALID'),0),COALESCE(SUM(regular_shifts*COALESCE(regular_rate,0)+overtime_shifts*COALESCE(overtime_rate,0)),0) FROM attendances WHERE worker_id=? AND work_date=?")){
                    ps.setInt(1,workerId);ps.setObject(2,workDate);
                    try(ResultSet rs=ps.executeQuery()){rs.next();
                        if(rs.getInt(1)==0)throw new BusinessException("Công nhân không có chấm công ngày "+workDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+".");
                        if(rs.getInt(2)>0)throw new BusinessException("Chấm công ngày này chưa hợp lệ. Hãy xử lý trước khi thanh toán.");
                        dayWage=rs.getBigDecimal(3);}
                }
                int periodId;LocalDate pFrom,pTo;boolean closed;
                try(PreparedStatement ps=c.prepareStatement("SELECT period_id,start_date,end_date,status FROM payroll_periods WHERE ? BETWEEN start_date AND end_date FOR UPDATE")){ps.setObject(1,workDate);try(ResultSet rs=ps.executeQuery()){
                    if(!rs.next())throw new BusinessException("Chưa có kỳ lương chứa ngày công này. Hãy tạo kỳ lương (Chấm công & lương → Tạo kỳ lương) trước.");
                    periodId=rs.getInt(1);pFrom=d(rs,2);pTo=d(rs,3);closed="CLOSED".equals(rs.getString(4));}}
                BigDecimal wkReg,wkOt,wkTotal;
                try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(regular_shifts),0),COALESCE(SUM(overtime_shifts),0),COALESCE(SUM(regular_shifts*COALESCE(regular_rate,0)+overtime_shifts*COALESCE(overtime_rate,0)),0) FROM attendances WHERE worker_id=? AND work_date BETWEEN ? AND ? AND validation_status='VALID'")){
                    ps.setInt(1,workerId);ps.setObject(2,pFrom);ps.setObject(3,pTo);try(ResultSet rs=ps.executeQuery()){rs.next();wkReg=rs.getBigDecimal(1);wkOt=rs.getBigDecimal(2);wkTotal=rs.getBigDecimal(3);}}
                int detailId=0;BigDecimal total=wkTotal;
                try(PreparedStatement ps=c.prepareStatement("SELECT detail_id,total_amount FROM payroll_details WHERE period_id=? AND worker_id=? FOR UPDATE")){ps.setInt(1,periodId);ps.setInt(2,workerId);try(ResultSet rs=ps.executeQuery()){if(rs.next()){detailId=rs.getInt(1);if(closed)total=rs.getBigDecimal(2);}}}
                if(detailId==0){
                    if(closed)throw new BusinessException("Kỳ lương đã chốt nhưng chưa có dòng lương của công nhân này. Hãy yêu cầu mở khóa kỳ để tính lại.");
                    try(PreparedStatement ps=c.prepareStatement("INSERT INTO payroll_details(period_id,worker_id,regular_shifts,overtime_shifts,total_amount,payment_status) VALUES(?,?,?,?,?,'UNPAID')",Statement.RETURN_GENERATED_KEYS)){ps.setInt(1,periodId);ps.setInt(2,workerId);ps.setBigDecimal(3,wkReg);ps.setBigDecimal(4,wkOt);ps.setBigDecimal(5,wkTotal);ps.executeUpdate();try(ResultSet k=ps.getGeneratedKeys()){k.next();detailId=k.getInt(1);}}
                }else if(!closed){
                    try(PreparedStatement ps=c.prepareStatement("UPDATE payroll_details SET regular_shifts=?,overtime_shifts=?,total_amount=? WHERE detail_id=?")){ps.setBigDecimal(1,wkReg);ps.setBigDecimal(2,wkOt);ps.setBigDecimal(3,wkTotal);ps.setInt(4,detailId);ps.executeUpdate();}
                }
                BigDecimal paidDay,paidAll;
                try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN work_date=? THEN amount END),0),COALESCE(SUM(amount),0) FROM wage_payments WHERE detail_id=?")){ps.setObject(1,workDate);ps.setInt(2,detailId);try(ResultSet rs=ps.executeQuery()){rs.next();paidDay=rs.getBigDecimal(1);paidAll=rs.getBigDecimal(2);}}
                if(paidDay.add(amount).compareTo(dayWage)>0)throw new BusinessException("Số tiền vượt tiền công của ngày này. Còn lại: "+dayWage.subtract(paidDay).stripTrailingZeros().toPlainString());
                if(paidAll.add(amount).compareTo(total)>0)throw new BusinessException("Số tiền thanh toán vượt số còn phải trả của kỳ: "+total.subtract(paidAll).stripTrailingZeros().toPlainString());
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO wage_payments(detail_id,method,amount,payment_date,reference_code,recorded_by,work_date) VALUES(?,?,?,?,?,?,?)")){ps.setInt(1,detailId);ps.setString(2,method);ps.setBigDecimal(3,amount);ps.setObject(4,payDate);ps.setString(5,stored);ps.setInt(6,userId);ps.setObject(7,workDate);ps.executeUpdate();}
                String st=paidAll.add(amount).compareTo(total)>=0?"PAID":"PARTIAL";
                try(PreparedStatement ps=c.prepareStatement("UPDATE payroll_details SET payment_status=? WHERE detail_id=?")){ps.setString(1,st);ps.setInt(2,detailId);ps.executeUpdate();}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof BusinessException b)throw b;throw e;}
        }catch(SQLException e){throw new BusinessException("Lỗi ghi nhận thanh toán hằng ngày: "+e.getMessage(),e);}
    }
    public void addPayment(int detailId,String method,BigDecimal amount,LocalDate day,String reference,String account,int userId){
        final String stored=checkPayment(method,amount,day,reference,account);
        try(Connection c=DBConnection.get()){
            c.setAutoCommit(false);
            try{
                int periodId;BigDecimal total;
                try(PreparedStatement ps=c.prepareStatement("SELECT d.period_id,d.total_amount FROM payroll_details d JOIN payroll_periods p ON p.period_id=d.period_id WHERE d.detail_id=? AND p.status='CLOSED' FOR UPDATE")){ps.setInt(1,detailId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new BusinessException("Chỉ thanh toán bảng lương của kỳ đã chốt.");periodId=rs.getInt(1);total=rs.getBigDecimal(2);}}
                BigDecimal paid;
                try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM wage_payments WHERE detail_id=?")){ps.setInt(1,detailId);try(ResultSet rs=ps.executeQuery()){rs.next();paid=rs.getBigDecimal(1);}}
                if(paid.add(amount).compareTo(total)>0)throw new BusinessException("Số tiền thanh toán vượt số tiền còn phải trả: "+total.subtract(paid));
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO wage_payments(detail_id,method,amount,payment_date,reference_code,recorded_by) VALUES(?,?,?,?,?,?)")){ps.setInt(1,detailId);ps.setString(2,method);ps.setBigDecimal(3,amount);ps.setObject(4,day);ps.setString(5,stored);ps.setInt(6,userId);ps.executeUpdate();}
                BigDecimal now=paid.add(amount);String st=now.compareTo(total)>=0?"PAID":"PARTIAL";
                try(PreparedStatement ps=c.prepareStatement("UPDATE payroll_details SET payment_status=? WHERE detail_id=?")){ps.setString(1,st);ps.setInt(2,detailId);ps.executeUpdate();}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof BusinessException b)throw b;throw e;}
        }catch(SQLException e){throw new BusinessException("Lỗi ghi nhận thanh toán: "+e.getMessage(),e);}
    }
    public List<Payment> payments(Integer periodId,Integer workerId,LocalDate from,LocalDate to,String status){
        StringBuilder sql=new StringBuilder("""
            SELECT p.payment_id,w.worker_code,w.full_name,CONCAT(pp.start_date,' – ',pp.end_date,IF(p.work_date IS NULL,'',CONCAT(' (trả ngày công ',DATE_FORMAT(p.work_date,'%d/%m/%Y'),')'))),p.method,p.amount,p.payment_date,p.reference_code
            FROM wage_payments p JOIN payroll_details d ON d.detail_id=p.detail_id JOIN payroll_periods pp ON pp.period_id=d.period_id JOIN workers w ON w.worker_id=d.worker_id WHERE 1=1""");
        List<Object> args=new ArrayList<>();
        if(periodId!=null){sql.append(" AND pp.period_id=?");args.add(periodId);}if(workerId!=null){sql.append(" AND w.worker_id=?");args.add(workerId);}if(from!=null){sql.append(" AND p.payment_date>=?");args.add(from);}if(to!=null){sql.append(" AND p.payment_date<=?");args.add(to);}if(status!=null){sql.append(" AND d.payment_status=?");args.add(status);}sql.append(" ORDER BY p.payment_date DESC,p.payment_id DESC");
        return query(sql.toString(),r->new Payment(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6),d(r,7),refOf(r.getString(8)),accountOf(r.getString(8))),args.toArray());
    }
    public Result paymentSummary(Integer periodId){
        return result("Báo cáo tình hình thanh toán lương",List.of("Trạng thái","Số công nhân","Tổng phải trả","Đã thanh toán","Còn nợ"),"""
            SELECT CASE d.payment_status WHEN 'UNPAID' THEN 'Chưa thanh toán' WHEN 'PARTIAL' THEN 'Thanh toán một phần' ELSE 'Đã thanh toán' END,
             COUNT(*),SUM(d.total_amount),SUM(COALESCE(p.paid,0)),SUM(d.total_amount-COALESCE(p.paid,0))
            FROM payroll_details d LEFT JOIN (SELECT detail_id,SUM(amount) paid FROM wage_payments GROUP BY detail_id) p ON p.detail_id=d.detail_id
            WHERE (? IS NULL OR d.period_id=?) GROUP BY d.payment_status ORDER BY d.payment_status""",periodId,periodId);
    }
    public Result laborCost(int periodId,Integer worksiteId){
        Period p=period(periodId);
        return laborCostRange("Chi phí nhân công theo công trình",p.from(),p.to(),worksiteId);
    }
    /** 2.8 – chi phí nhân công theo công trình trong một tháng dương lịch (tính từ chấm công hợp lệ của tháng đó). */
    public Result laborCostMonth(YearMonth month,Integer worksiteId){
        return laborCostRange("Chi phí nhân công theo công trình - tháng "+month.getMonthValue()+"/"+month.getYear(),month.atDay(1),month.atEndOfMonth(),worksiteId);
    }
    private Result laborCostRange(String title,LocalDate from,LocalDate to,Integer worksiteId){
        return result(title,List.of("Mã CT","Công trình","Số công nhân","Ca thường","Ca tăng ca","Tổng chi phí"),"""
            SELECT s.worksite_code,s.worksite_name,COUNT(DISTINCT a.worker_id),SUM(a.regular_shifts),SUM(a.overtime_shifts),
             SUM(a.regular_shifts*a.regular_rate+a.overtime_shifts*a.overtime_rate)
            FROM attendances a JOIN worksites s ON s.worksite_id=a.worksite_id
            WHERE a.work_date BETWEEN ? AND ? AND a.validation_status='VALID' AND (? IS NULL OR a.worksite_id=?)
            GROUP BY s.worksite_id,s.worksite_code,s.worksite_name ORDER BY s.worksite_code""",from,to,worksiteId,worksiteId);
    }
    public Result topIncome(int periodId,int limit,boolean highest){
        if(limit<1||limit>100)throw new BusinessException("Số lượng cần liệt kê từ 1 đến 100.");
        String order=highest?"DESC":"ASC";
        return result(highest?"Công nhân có thu nhập cao nhất":"Công nhân có thu nhập thấp nhất",List.of("Mã CN","Họ tên","Ca thường","Ca tăng ca","Thu nhập"),
                "SELECT w.worker_code,w.full_name,d.regular_shifts,d.overtime_shifts,d.total_amount FROM payroll_details d JOIN workers w ON w.worker_id=d.worker_id WHERE d.period_id=? ORDER BY d.total_amount "+order+",w.worker_code LIMIT ?",periodId,limit);
    }
    /** 2.8 – công nhân có thu nhập cao nhất / thấp nhất trong một tháng dương lịch (cộng dồn chấm công hợp lệ của tháng). */
    public Result topIncomeMonth(YearMonth month,int limit,boolean highest){
        if(limit<1||limit>100)throw new BusinessException("Số lượng cần liệt kê từ 1 đến 100.");
        String order=highest?"DESC":"ASC";
        String t=(highest?"Công nhân có thu nhập cao nhất":"Công nhân có thu nhập thấp nhất")+" - tháng "+month.getMonthValue()+"/"+month.getYear();
        return result(t,List.of("Mã CN","Họ tên","Ca thường","Ca tăng ca","Thu nhập"),"""
            SELECT w.worker_code,w.full_name,SUM(a.regular_shifts),SUM(a.overtime_shifts),SUM(a.regular_shifts*a.regular_rate+a.overtime_shifts*a.overtime_rate) AS income
            FROM attendances a JOIN workers w ON w.worker_id=a.worker_id
            WHERE a.work_date BETWEEN ? AND ? AND a.validation_status='VALID'
            GROUP BY w.worker_id,w.worker_code,w.full_name ORDER BY income %s,w.worker_code LIMIT ?""".formatted(order),month.atDay(1),month.atEndOfMonth(),limit);
    }
    public Result detailByWorker(int periodId,int workerId){
        Period p=period(periodId);
        return result("Chi tiết tiền công",List.of("Ngày","Công trình","Ca thường","Đơn giá thường","Tiền thường","Ca tăng ca","Đơn giá tăng ca","Tiền tăng ca","Tổng"),"""
            SELECT a.work_date,s.worksite_name,a.regular_shifts,a.regular_rate,a.regular_shifts*a.regular_rate,
             a.overtime_shifts,a.overtime_rate,a.overtime_shifts*a.overtime_rate,
             a.regular_shifts*a.regular_rate+a.overtime_shifts*a.overtime_rate
            FROM attendances a JOIN worksites s ON s.worksite_id=a.worksite_id
            WHERE a.worker_id=? AND a.work_date BETWEEN ? AND ? AND a.validation_status='VALID' ORDER BY a.work_date""",workerId,p.from(),p.to());
    }
    private static int scalar(String sql,Object...p){return query(sql,r->r.getInt(1),p).get(0);}
    private static void exec(String sql,Object...p){try(Connection c=DBConnection.get();PreparedStatement ps=c.prepareStatement(sql)){for(int i=0;i<p.length;i++)ps.setObject(i+1,p[i]);ps.executeUpdate();}catch(SQLException e){throw new BusinessException("Lỗi cập nhật kỳ lương: "+e.getMessage(),e);}}
    private static Result result(String title,List<String> h,String sql,Object...p){List<List<Object>> rows=query(sql,r->{List<Object>x=new ArrayList<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)x.add(r.getObject(i));return x;},p);return new Result(title,h,rows);}
    @FunctionalInterface private interface Mapper<T>{T map(ResultSet r)throws SQLException;}
    private static <T>List<T> query(String sql,Mapper<T> mapper,Object...p){try(Connection c=DBConnection.get();PreparedStatement ps=c.prepareStatement(sql)){for(int i=0;i<p.length;i++)ps.setObject(i+1,p[i]);try(ResultSet rs=ps.executeQuery()){List<T> out=new ArrayList<>();while(rs.next())out.add(mapper.map(rs));return out;}}catch(SQLException e){throw new BusinessException("Lỗi truy vấn lương: "+e.getMessage(),e);}}
}
